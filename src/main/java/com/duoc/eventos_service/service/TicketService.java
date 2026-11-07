package com.duoc.eventos_service.service;

import com.duoc.eventos_service.dto.*;
import com.duoc.eventos_service.exception.*;
import com.duoc.eventos_service.model.Compra;
import com.duoc.eventos_service.model.EstadoTicket;
import com.duoc.eventos_service.model.Ticket;
import com.duoc.eventos_service.repository.CompraRepository;
import com.duoc.eventos_service.repository.EventoRepository;
import com.duoc.eventos_service.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Ciclo de vida del ticket: se genera en EFS, se sube automáticamente a S3 en evento{id}/{usuario}/ticket{nnn}.pdf,
 * se modifica reemplazando el mismo archivo, se descarga verificando su SHA-256 y se elimina validando que exista.
 */
@Service
@Transactional(readOnly = true)
public class TicketService {

    private static final Logger LOG = LoggerFactory.getLogger(TicketService.class);
    private static final String PDF = "application/pdf";

    private final TicketRepository tickets;
    private final CompraRepository compras;
    private final EventoRepository eventos;
    private final PdfService pdf;
    private final EfsStorageService efs;
    private final AwsS3Service s3;
    private final Clock reloj;

    public TicketService(TicketRepository tickets, CompraRepository compras, EventoRepository eventos,
                         PdfService pdf, EfsStorageService efs, AwsS3Service s3, Clock reloj) {
        this.tickets = tickets;
        this.compras = compras;
        this.eventos = eventos;
        this.pdf = pdf;
        this.efs = efs;
        this.s3 = s3;
        this.reloj = reloj;
    }

    /** Clave en S3 (y ruta relativa en EFS) según el formato del caso: /evento123/usuarioX/ticket001.pdf. */
    public static String clave(Long eventoId, String usuario, Long ticketId) {
        return "evento" + eventoId + "/" + usuario + "/" + PdfService.numero(ticketId) + ".pdf";
    }

    @Transactional
    public TicketResponse generar(TicketRequest req) {
        Compra compra = compras.buscarConEvento(req.compraId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la compra con id " + req.compraId()));
        if (tickets.existsByCompraIdAndEstadoNot(compra.getId(), EstadoTicket.ELIMINADO)) {
            throw new ConflictoException("La compra " + compra.getId() + " ya tiene un ticket vigente. "
                    + "Use PUT /api/tickets/{id} para modificarlo");
        }

        Ticket ticket = new Ticket();
        ticket.setCompra(compra);
        ticket.setCodigo(UUID.randomUUID().toString());
        ticket.setNombreAsistente(req.nombreAsistente().trim());
        ticket.setCorreoAsistente(req.correoAsistente().trim().toLowerCase(Locale.ROOT));
        ticket.setEstado(EstadoTicket.GENERADO);
        ticket.setVersion(1);
        ticket.setFechaCreacion(ahora());
        tickets.save(ticket);

        String clave = clave(compra.getEvento().getId(), compra.getUsuario(), ticket.getId());
        Path ruta = efs.rutaTicket(clave);
        ticket.setS3Key(clave);
        ticket.setRutaEfs(ruta.toString());
        guardarEnEfs(ticket, ruta);

        String aviso = subirSinFallar(ticket);
        return aRespuesta(ticket, aviso);
    }

    /** Sube (o vuelve a subir) a S3 el PDF que está en EFS. */
    @Transactional
    public TicketResponse subir(Long id) {
        Ticket ticket = vigente(id);
        Path ruta = Path.of(ticket.getRutaEfs());
        if (!efs.existe(ruta)) {
            LOG.warn("El ticket {} no estaba en EFS; se regenera antes de subirlo", id);
            guardarEnEfs(ticket, ruta);
        }
        subirDesdeEfs(ticket);
        return aRespuesta(ticket, null);
    }

    public TicketResponse obtener(Long id) {
        return aRespuesta(buscar(id), null);
    }

    /** Descarga desde S3 y comprueba que el SHA-256 coincida con el registrado al generar el archivo. */
    public ArchivoDescarga descargar(Long id) {
        Ticket ticket = vigente(id);
        if (ticket.getEstado() != EstadoTicket.SUBIDO) {
            throw new ConflictoException("El ticket " + id + " aún no está en S3. Use POST /api/tickets/" + id
                    + "/subir");
        }
        byte[] datos = descargarVerificado(ticket);
        return new ArchivoDescarga(PdfService.numero(ticket.getId()) + ".pdf", PDF, datos,
                ticket.getChecksumSha256());
    }

    /** ZIP con los tickets en S3 de un usuario, de un evento o de ambos filtros a la vez. */
    public ArchivoDescarga descargarZip(String usuario, Long eventoId) {
        if ((usuario == null || usuario.isBlank()) && eventoId == null) {
            throw new SolicitudInvalidaException("Indique el parámetro 'usuario', 'eventoId' o ambos");
        }
        String filtroUsuario = usuario == null || usuario.isBlank() ? null : CompraService.normalizarUsuario(usuario);
        if (eventoId != null && !eventos.existsById(eventoId)) {
            throw new RecursoNoEncontradoException("No existe el evento con id " + eventoId);
        }
        List<Ticket> lista = tickets.historial(filtroUsuario, eventoId, EstadoTicket.SUBIDO, null, null);
        if (lista.isEmpty()) {
            throw new RecursoNoEncontradoException("No hay tickets en S3 para los filtros indicados");
        }

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        StringBuilder manifiesto = new StringBuilder();
        try (ZipOutputStream zip = new ZipOutputStream(salida, StandardCharsets.UTF_8)) {
            for (Ticket t : lista) {
                byte[] datos = descargarVerificado(t);
                zip.putNextEntry(new ZipEntry(t.getS3Key()));
                zip.write(datos);
                zip.closeEntry();
                manifiesto.append(t.getChecksumSha256()).append("  ").append(t.getS3Key()).append('\n');
            }
            zip.putNextEntry(new ZipEntry("SHA256SUMS.txt"));
            zip.write(manifiesto.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException e) {
            throw new AlmacenamientoException("No fue posible armar el archivo ZIP", e);
        }
        String nombre = "tickets" + (eventoId != null ? "_evento" + eventoId : "")
                + (filtroUsuario != null ? "_" + filtroUsuario : "") + ".zip";
        byte[] zipBytes = salida.toByteArray();
        return new ArchivoDescarga(nombre, "application/zip", zipBytes, Checksums.sha256Hex(zipBytes));
    }

    /**
     * Modifica cantidad, nombre o correo, regenera el PDF y lo reemplaza en la MISMA ruta de EFS y la MISMA clave
     * de S3 (corrige la observación de S2). Evento y usuario no se pueden cambiar porque definen la carpeta.
     */
    @Transactional
    public TicketResponse modificar(Long id, TicketUpdateRequest req) {
        if (req.eventoId() != null || req.usuario() != null) {
            throw new SolicitudInvalidaException("No se puede cambiar el evento ni el usuario de un ticket: definen "
                    + "su carpeta en S3. Elimine el ticket y genere uno nuevo para otra compra");
        }
        if (req.cantidad() == null && req.nombreAsistente() == null && req.correoAsistente() == null) {
            throw new SolicitudInvalidaException("Indique al menos un dato a modificar: cantidad, nombreAsistente "
                    + "o correoAsistente");
        }
        if (req.nombreAsistente() != null && req.nombreAsistente().isBlank()) {
            throw new SolicitudInvalidaException("El nombre del asistente no puede quedar vacío");
        }

        Ticket ticket = vigente(id);
        Compra compra = ticket.getCompra();
        if (req.cantidad() != null && !req.cantidad().equals(compra.getCantidad())) {
            eventos.bloquear(compra.getEvento().getId());
            long reservadasOtras = compras.entradasReservadas(compra.getEvento().getId()) - compra.getCantidad();
            CompraService.validarCapacidad(compra.getEvento(), reservadasOtras, req.cantidad());
            compra.setCantidad(req.cantidad());
            compra.setPrecioTotal(compra.getPrecioUnitario().multiply(BigDecimal.valueOf(req.cantidad())));
        }
        if (req.nombreAsistente() != null) {
            ticket.setNombreAsistente(req.nombreAsistente().trim());
        }
        if (req.correoAsistente() != null) {
            ticket.setCorreoAsistente(req.correoAsistente().trim().toLowerCase(Locale.ROOT));
        }
        ticket.setVersion(ticket.getVersion() + 1);
        ticket.setFechaModificacion(ahora());

        Path ruta = Path.of(ticket.getRutaEfs());
        byte[] anterior = efs.existe(ruta) ? efs.leer(ruta) : null;
        guardarEnEfs(ticket, ruta);
        if (ticket.getEstado() == EstadoTicket.SUBIDO) {
            try {
                subirDesdeEfs(ticket);
            } catch (RuntimeException e) {
                if (anterior != null) {
                    efs.escribir(ruta, anterior);
                }
                throw e;
            }
            return aRespuesta(ticket, null);
        }
        return aRespuesta(ticket, subirSinFallar(ticket));
    }

    /** Valida que el ticket exista; borra el PDF de S3 (si existe allí) y de EFS, y lo marca como ELIMINADO. */
    @Transactional
    public TicketResponse eliminar(Long id) {
        Ticket ticket = vigente(id);
        List<String> avisos = new ArrayList<>();
        if (ticket.getEstado() == EstadoTicket.SUBIDO) {
            String bucket = s3.bucketTickets();
            if (s3.existe(bucket, ticket.getS3Key())) {
                s3.eliminar(bucket, ticket.getS3Key());
                avisos.add("Eliminado de S3: " + ticket.getS3Key());
            } else {
                avisos.add("El archivo ya no estaba en S3: " + ticket.getS3Key());
            }
        }
        if (efs.eliminar(Path.of(ticket.getRutaEfs()))) {
            avisos.add("Eliminado de EFS: " + ticket.getRutaEfs());
        }
        ticket.setEstado(EstadoTicket.ELIMINADO);
        ticket.setFechaEliminacion(ahora());
        return aRespuesta(ticket, String.join(". ", avisos));
    }

    /** Historial de tickets generados con filtros combinables; incluye los eliminados para trazabilidad. */
    public HistorialResponse historial(String usuario, Long eventoId, String estado, LocalDate desde,
                                       LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new SolicitudInvalidaException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        EstadoTicket filtroEstado = null;
        if (estado != null && !estado.isBlank()) {
            try {
                filtroEstado = EstadoTicket.valueOf(estado.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new SolicitudInvalidaException("Estado inválido. Valores permitidos: "
                        + Arrays.toString(EstadoTicket.values()));
            }
        }
        String filtroUsuario = usuario == null || usuario.isBlank() ? null : CompraService.normalizarUsuario(usuario);
        List<TicketResponse> lista = tickets.historial(filtroUsuario, eventoId, filtroEstado,
                        desde == null ? null : desde.atStartOfDay(),
                        hasta == null ? null : hasta.plusDays(1).atStartOfDay())
                .stream().map(t -> aRespuesta(t, null)).toList();

        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("usuario", filtroUsuario);
        filtros.put("eventoId", eventoId);
        filtros.put("estado", filtroEstado);
        filtros.put("desde", desde);
        filtros.put("hasta", hasta);
        filtros.values().removeIf(Objects::isNull);
        return new HistorialResponse(lista.size(), filtros, lista);
    }

    private void guardarEnEfs(Ticket ticket, Path ruta) {
        byte[] datos = pdf.ticket(ticket, ticket.getS3Key());
        efs.escribir(ruta, datos);
        ticket.setChecksumSha256(Checksums.sha256Hex(datos));
        ticket.setTamanoBytes((long) datos.length);
    }

    private void subirDesdeEfs(Ticket ticket) {
        byte[] datos = efs.leer(Path.of(ticket.getRutaEfs()));
        if (!Checksums.sha256Hex(datos).equals(ticket.getChecksumSha256())) {
            throw new IntegridadArchivoException("El archivo en EFS no coincide con el checksum registrado del "
                    + "ticket " + ticket.getId());
        }
        Compra compra = ticket.getCompra();
        Map<String, String> metadatos = Map.of(
                "evento-id", String.valueOf(compra.getEvento().getId()),
                "usuario", compra.getUsuario(),
                "ticket-codigo", ticket.getCodigo(),
                "version", String.valueOf(ticket.getVersion()),
                "fecha-emision", ticket.getFechaCreacion().toString());
        s3.subir(s3.bucketTickets(), ticket.getS3Key(), datos, PDF, metadatos);
        ticket.setEstado(EstadoTicket.SUBIDO);
        ticket.setFechaSubida(ahora());
    }

    /** Subida automática: si S3 no responde, el ticket queda en EFS (GENERADO) y se informa cómo reintentar. */
    private String subirSinFallar(Ticket ticket) {
        try {
            subirDesdeEfs(ticket);
            return null;
        } catch (AlmacenamientoException | S3AccesoDenegadoException | RecursoNoEncontradoException e) {
            LOG.warn("Ticket {} guardado en EFS, pero no se pudo subir a S3: {}", ticket.getId(), e.getMessage());
            return "El ticket quedó guardado en EFS, pero no se pudo subir a S3 (" + e.getMessage()
                    + "). Reintente con POST /api/tickets/" + ticket.getId() + "/subir";
        }
    }

    private byte[] descargarVerificado(Ticket ticket) {
        byte[] datos = s3.descargar(s3.bucketTickets(), ticket.getS3Key());
        if (!Checksums.sha256Hex(datos).equals(ticket.getChecksumSha256())) {
            throw new IntegridadArchivoException("El archivo descargado de S3 (" + ticket.getS3Key()
                    + ") no coincide con su checksum SHA-256 registrado");
        }
        return datos;
    }

    private Ticket buscar(Long id) {
        return tickets.buscarCompleto(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el ticket con id " + id));
    }

    private Ticket vigente(Long id) {
        Ticket ticket = buscar(id);
        if (ticket.getEstado() == EstadoTicket.ELIMINADO) {
            throw new RecursoNoEncontradoException("El ticket " + id + " fue eliminado");
        }
        return ticket;
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).withNano(0);
    }

    static TicketResponse aRespuesta(Ticket t, String aviso) {
        Compra c = t.getCompra();
        return new TicketResponse(t.getId(), PdfService.numero(t.getId()), t.getCodigo(), c.getId(),
                c.getEvento().getId(), c.getEvento().getNombre(), c.getUsuario(), t.getNombreAsistente(),
                t.getCorreoAsistente(), c.getCantidad(), c.getPrecioUnitario(), c.getPrecioTotal(),
                t.getEstado().name(), t.getVersion(), t.getS3Key(), t.getRutaEfs(), t.getChecksumSha256(),
                t.getTamanoBytes(), t.getFechaCreacion(), t.getFechaModificacion(), t.getFechaSubida(),
                t.getFechaEliminacion(), aviso == null || aviso.isBlank() ? null : aviso);
    }
}

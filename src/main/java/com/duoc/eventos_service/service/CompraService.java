package com.duoc.eventos_service.service;

import com.duoc.eventos_service.dto.CompraRequest;
import com.duoc.eventos_service.dto.CompraResponse;
import com.duoc.eventos_service.exception.ConflictoException;
import com.duoc.eventos_service.exception.RecursoNoEncontradoException;
import com.duoc.eventos_service.model.Compra;
import com.duoc.eventos_service.model.Evento;
import com.duoc.eventos_service.repository.CompraRepository;
import com.duoc.eventos_service.repository.EventoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class CompraService {

    private final CompraRepository compras;
    private final EventoRepository eventos;
    private final PdfService pdf;
    private final Clock reloj;

    public CompraService(CompraRepository compras, EventoRepository eventos, PdfService pdf, Clock reloj) {
        this.compras = compras;
        this.eventos = eventos;
        this.pdf = pdf;
        this.reloj = reloj;
    }

    /** Registra la reserva: valida la capacidad y calcula el total con el precio vigente del evento. */
    @Transactional
    public CompraResponse crear(CompraRequest req) {
        Evento evento = eventos.bloquear(req.eventoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el evento con id " + req.eventoId()));
        validarCapacidad(evento, compras.entradasReservadas(evento.getId()), req.cantidad());

        Compra compra = new Compra();
        compra.setEvento(evento);
        compra.setUsuario(normalizarUsuario(req.usuario()));
        compra.setCantidad(req.cantidad());
        compra.setPrecioUnitario(evento.getPrecio());
        compra.setPrecioTotal(evento.getPrecio().multiply(BigDecimal.valueOf(req.cantidad())));
        compra.setFechaCompra(LocalDateTime.now(reloj).withNano(0));
        return aRespuesta(compras.save(compra));
    }

    public CompraResponse obtener(Long id) {
        return aRespuesta(buscar(id));
    }

    /** Resumen descargable con los datos reales de la compra (corrige la observación de S2). */
    public byte[] resumenPdf(Long id) {
        return pdf.resumenCompra(buscar(id), LocalDateTime.now(reloj).withNano(0));
    }

    Compra buscar(Long id) {
        return compras.buscarConEvento(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la compra con id " + id));
    }

    static void validarCapacidad(Evento evento, long reservadas, int solicitadas) {
        long disponibles = evento.getCapacidad() - reservadas;
        if (solicitadas > disponibles) {
            throw new ConflictoException("No hay entradas suficientes para '" + evento.getNombre()
                    + "': disponibles " + Math.max(0, disponibles) + ", solicitadas " + solicitadas);
        }
    }

    static String normalizarUsuario(String usuario) {
        return usuario.trim().toLowerCase(Locale.ROOT);
    }

    static CompraResponse aRespuesta(Compra c) {
        return new CompraResponse(c.getId(), c.getEvento().getId(), c.getEvento().getNombre(), c.getUsuario(),
                c.getCantidad(), c.getPrecioUnitario(), c.getPrecioTotal(), c.getFechaCompra());
    }
}

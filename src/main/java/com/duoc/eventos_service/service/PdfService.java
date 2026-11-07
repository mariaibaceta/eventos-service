package com.duoc.eventos_service.service;

import com.duoc.eventos_service.exception.AlmacenamientoException;
import com.duoc.eventos_service.model.Compra;
import com.duoc.eventos_service.model.Evento;
import com.duoc.eventos_service.model.Ticket;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Genera el ticket personalizado y el resumen de compra en PDF con los datos reales de la base de datos. */
@Service
public class PdfService {

    private static final PDType1Font NORMAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font NEGRITA = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final Color AZUL = new Color(22, 52, 92);
    private static final Color GRIS = new Color(110, 110, 110);
    private static final Color FONDO = new Color(242, 245, 249);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final float MARGEN = 50;

    public byte[] ticket(Ticket ticket, String s3Key) {
        Compra compra = ticket.getCompra();
        Evento evento = compra.getEvento();
        List<String[]> filas = new ArrayList<>();
        filas.add(new String[]{"Asistente", ticket.getNombreAsistente()});
        filas.add(new String[]{"Correo", ticket.getCorreoAsistente()});
        filas.add(new String[]{"Usuario", compra.getUsuario()});
        filas.add(new String[]{"Cantidad de entradas", String.valueOf(compra.getCantidad())});
        filas.add(new String[]{"Precio unitario", dinero(compra.getPrecioUnitario())});
        filas.add(new String[]{"Total pagado", dinero(compra.getPrecioTotal())});
        filas.add(new String[]{"Compra N.º", String.valueOf(compra.getId())});
        filas.add(new String[]{"Fecha de compra", FECHA.format(compra.getFechaCompra())});
        filas.add(new String[]{"Emitido", FECHA.format(ticket.getFechaCreacion())});
        if (ticket.getFechaModificacion() != null) {
            filas.add(new String[]{"Modificado", FECHA.format(ticket.getFechaModificacion())
                    + " (versión " + ticket.getVersion() + ")"});
        }
        filas.add(new String[]{"Código", ticket.getCodigo()});
        String titulo = "TICKET N.º %03d".formatted(ticket.getId());
        return documento(titulo, "Ticket " + numero(ticket.getId()) + " - " + evento.getNombre(), evento, filas,
                "Ubicación en S3: " + s3Key);
    }

    public byte[] resumenCompra(Compra compra, LocalDateTime emitido) {
        Evento evento = compra.getEvento();
        List<String[]> filas = new ArrayList<>();
        filas.add(new String[]{"Compra N.º", String.valueOf(compra.getId())});
        filas.add(new String[]{"Usuario", compra.getUsuario()});
        filas.add(new String[]{"Número de entradas", String.valueOf(compra.getCantidad())});
        filas.add(new String[]{"Precio unitario", dinero(compra.getPrecioUnitario())});
        filas.add(new String[]{"Precio total", dinero(compra.getPrecioTotal())});
        filas.add(new String[]{"Fecha de compra", FECHA.format(compra.getFechaCompra())});
        filas.add(new String[]{"Documento emitido", FECHA.format(emitido)});
        return documento("RESUMEN DE COMPRA", "Resumen de compra " + compra.getId(), evento, filas,
                "Resumen generado con los datos registrados de la compra.");
    }

    public static String numero(Long id) {
        return "ticket%03d".formatted(id);
    }

    private byte[] documento(String titulo, String tituloDocumento, Evento evento, List<String[]> filas, String pie) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            float ancho = pagina.getMediaBox().getWidth();
            float alto = pagina.getMediaBox().getHeight();

            try (PDPageContentStream cs = new PDPageContentStream(doc, pagina)) {
                // Banda superior
                cs.setNonStrokingColor(AZUL);
                cs.addRect(0, alto - 110, ancho, 110);
                cs.fill();
                texto(cs, NEGRITA, 22, Color.WHITE, MARGEN, alto - 60, titulo, ancho - 2 * MARGEN);
                texto(cs, NORMAL, 11, Color.WHITE, MARGEN, alto - 85, "Sistema de reservas de eventos",
                        ancho - 2 * MARGEN);

                // Datos del evento
                float y = alto - 160;
                texto(cs, NEGRITA, 18, AZUL, MARGEN, y, evento.getNombre(), ancho - 2 * MARGEN);
                y -= 22;
                String cuando = "Fecha del evento: " + FECHA.format(evento.getFecha());
                if (evento.getLugar() != null && !evento.getLugar().isBlank()) {
                    cuando += "   ·   Lugar: " + evento.getLugar();
                }
                texto(cs, NORMAL, 11, GRIS, MARGEN, y, cuando, ancho - 2 * MARGEN);

                // Tabla de detalle
                y -= 30;
                float altoFila = 26;
                cs.setNonStrokingColor(FONDO);
                cs.addRect(MARGEN - 10, y - filas.size() * altoFila - 2, ancho - 2 * MARGEN + 20,
                        filas.size() * altoFila + 14);
                cs.fill();
                for (String[] fila : filas) {
                    y -= altoFila;
                    texto(cs, NEGRITA, 11, AZUL, MARGEN, y + 10, fila[0], 160);
                    texto(cs, NORMAL, 11, Color.BLACK, MARGEN + 170, y + 10, fila[1], ancho - 2 * MARGEN - 170);
                }

                // Pie
                cs.setStrokingColor(GRIS);
                cs.moveTo(MARGEN, 70);
                cs.lineTo(ancho - MARGEN, 70);
                cs.stroke();
                texto(cs, NORMAL, 9, GRIS, MARGEN, 55, pie, ancho - 2 * MARGEN);
                texto(cs, NORMAL, 9, GRIS, MARGEN, 42, "Presente este documento al ingresar al evento.",
                        ancho - 2 * MARGEN);
            }

            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle(limpiar(NORMAL, tituloDocumento));
            info.setSubject(limpiar(NORMAL, evento.getNombre()));
            info.setCreator("eventos-service");
            doc.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new AlmacenamientoException("No fue posible generar el PDF", e);
        }
    }

    private static void texto(PDPageContentStream cs, PDType1Font fuente, float tamano, Color color, float x, float y,
                              String valor, float anchoMaximo) throws IOException {
        String linea = ajustar(fuente, tamano, limpiar(fuente, valor), anchoMaximo);
        cs.beginText();
        cs.setNonStrokingColor(color);
        cs.setFont(fuente, tamano);
        cs.newLineAtOffset(x, y);
        cs.showText(linea);
        cs.endText();
    }

    /** Las fuentes estándar usan WinAnsiEncoding: los caracteres que no puede representar se reemplazan por '?'. */
    static String limpiar(PDType1Font fuente, String valor) {
        if (valor == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        valor.codePoints().forEach(cp -> {
            String c = new String(Character.toChars(cp));
            if (Character.isISOControl(cp)) {
                sb.append(' ');
                return;
            }
            try {
                fuente.encode(c);
                sb.append(c);
            } catch (IOException | IllegalArgumentException e) {
                sb.append('?');
            }
        });
        return sb.toString();
    }

    private static String ajustar(PDType1Font fuente, float tamano, String valor, float anchoMaximo)
            throws IOException {
        if (ancho(fuente, tamano, valor) <= anchoMaximo) {
            return valor;
        }
        String recorte = valor;
        while (!recorte.isEmpty() && ancho(fuente, tamano, recorte + "...") > anchoMaximo) {
            recorte = recorte.substring(0, recorte.length() - 1);
        }
        return recorte + "...";
    }

    private static float ancho(PDType1Font fuente, float tamano, String valor) throws IOException {
        return fuente.getStringWidth(valor) / 1000 * tamano;
    }

    static String dinero(BigDecimal monto) {
        DecimalFormatSymbols simbolos = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-CL"));
        boolean entero = monto.stripTrailingZeros().scale() <= 0;
        DecimalFormat formato = new DecimalFormat(entero ? "#,##0" : "#,##0.00", simbolos);
        return "$" + formato.format(monto);
    }
}

package com.duoc.eventos_service;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketsTest extends PruebaBase {

    private static String sha256(byte[] datos) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(datos));
    }

    @Test
    @DisplayName("Generar: el PDF se guarda en EFS y se sube solo a S3 en evento{id}/{usuario}/ticket{nnn}.pdf")
    void generaEnEfsYSubeAS3() throws Exception {
        long evento = crearEvento("Rock en Viña", "30000", 100);
        String usuario = usuarioNuevo("ana");
        long compra = crearCompra(evento, usuario, 2);

        String json = generarTicket(compra);
        long id = id(json);
        String clave = "evento%d/%s/ticket%03d.pdf".formatted(evento, usuario, id);
        assertThat((String) JsonPath.read(json, "$.s3Key")).isEqualTo(clave);
        assertThat((String) JsonPath.read(json, "$.estado")).isEqualTo("SUBIDO");
        assertThat((String) JsonPath.read(json, "$.numero")).isEqualTo("ticket%03d".formatted(id));

        Path enEfs = efs().resolve("tickets").resolve(clave);
        assertThat((String) JsonPath.read(json, "$.rutaEfs")).isEqualTo(enEfs.toString());
        assertThat(enEfs).exists();
        byte[] pdf = Files.readAllBytes(enEfs);
        assertThat(s3.objetos()).containsKey(clave);
        assertThat(s3.objetos().get(clave).datos()).isEqualTo(pdf);
        assertThat(s3.objetos().get(clave).contentType()).isEqualTo("application/pdf");
        assertThat(s3.objetos().get(clave).metadatos()).containsEntry("usuario", usuario)
                .containsEntry("evento-id", String.valueOf(evento));
        assertThat((String) JsonPath.read(json, "$.checksumSha256")).isEqualTo(sha256(pdf));

        assertThat(textoPdf(pdf)).contains("Rock en Viña", "Ana Pérez", "Cantidad de entradas 2",
                "Precio unitario $30.000", "Total pagado $60.000", usuario);
    }

    @Test
    @DisplayName("Una compra admite un solo ticket vigente (409) y la compra debe existir (404)")
    void unTicketPorCompra() throws Exception {
        long compra = crearCompra(crearEvento("Jazz", "15000", 20), usuarioNuevo("bea"), 1);
        generarTicket(compra);
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"compraId\":%d,\"nombreAsistente\":\"Bea\",\"correoAsistente\":\"b@c.cl\"}"
                                .formatted(compra)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"compraId\":99999,\"nombreAsistente\":\"Bea\",\"correoAsistente\":\"b@c.cl\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"compraId\":%d,\"nombreAsistente\":\"\",\"correoAsistente\":\"no-es-correo\"}"
                                .formatted(compra)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasSize(2)));
    }

    @Test
    @DisplayName("Si S3 no responde, el ticket queda en EFS (GENERADO) y luego se sube con POST /subir")
    void subidaManualCuandoS3Falla() throws Exception {
        long evento = crearEvento("Ópera", "50000", 10);
        String usuario = usuarioNuevo("ciro");
        long compra = crearCompra(evento, usuario, 1);
        s3.setCaido(true);
        String json = generarTicket(compra);
        long id = id(json);
        assertThat((String) JsonPath.read(json, "$.estado")).isEqualTo("GENERADO");
        assertThat((String) JsonPath.read(json, "$.aviso")).contains("/api/tickets/" + id + "/subir");
        assertThat(efs().resolve("tickets").resolve((String) JsonPath.read(json, "$.s3Key"))).exists();
        mvc.perform(get("/api/tickets/" + id + "/descargar")).andExpect(status().isConflict());

        s3.setCaido(false);
        mvc.perform(post("/api/tickets/" + id + "/subir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SUBIDO"))
                .andExpect(jsonPath("$.fechaSubida").exists());
        assertThat(s3.objetos()).containsKey("evento%d/%s/ticket%03d.pdf".formatted(evento, usuario, id));
    }

    @Test
    @DisplayName("Descargar: llega el mismo PDF que está en S3 y la cabecera trae su SHA-256")
    void descargaConIntegridad() throws Exception {
        long compra = crearCompra(crearEvento("Cine", "5000", 30), usuarioNuevo("dani"), 2);
        String json = generarTicket(compra);
        long id = id(json);
        String clave = JsonPath.read(json, "$.s3Key");

        byte[] pdf = mvc.perform(get("/api/tickets/" + id + "/descargar"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("ticket%03d.pdf".formatted(id))))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(pdf).isEqualTo(s3.objetos().get(clave).datos());
        assertThat(sha256(pdf)).isEqualTo(JsonPath.read(json, "$.checksumSha256"));

        s3.corromper(clave);
        mvc.perform(get("/api/tickets/" + id + "/descargar"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.mensaje", containsString("checksum")));
        mvc.perform(get("/api/tickets/99999/descargar")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Descargar por usuario o por evento entrega un ZIP con la estructura de carpetas y SHA256SUMS")
    void descargaZip() throws Exception {
        long evento = crearEvento("Feria", "2000", 100);
        String ana = usuarioNuevo("ana");
        String beto = usuarioNuevo("beto");
        generarTicket(crearCompra(evento, ana, 1));
        generarTicket(crearCompra(evento, ana, 2));
        generarTicket(crearCompra(evento, beto, 1));

        Map<String, byte[]> porUsuario = zip(mvc.perform(get("/api/tickets/descargar").param("usuario", ana))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(porUsuario.keySet()).hasSize(3).contains("SHA256SUMS.txt")
                .allMatch(n -> n.equals("SHA256SUMS.txt") || n.startsWith("evento" + evento + "/" + ana + "/"));

        Map<String, byte[]> porEvento = zip(mvc.perform(get("/api/tickets/descargar")
                        .param("eventoId", String.valueOf(evento)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(porEvento).hasSize(4);
        String sumas = new String(porEvento.get("SHA256SUMS.txt"), StandardCharsets.UTF_8);
        for (Map.Entry<String, byte[]> e : porEvento.entrySet()) {
            if (!e.getKey().equals("SHA256SUMS.txt")) {
                assertThat(sumas).contains(sha256(e.getValue()) + "  " + e.getKey());
            }
        }

        mvc.perform(get("/api/tickets/descargar")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/tickets/descargar").param("usuario", "nadie123")).andExpect(status().isNotFound());
        mvc.perform(get("/api/tickets/descargar").param("eventoId", "99999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Modificar reemplaza el PDF en la MISMA clave de S3 y la MISMA ruta de EFS, con datos nuevos")
    void modificaEnLaMismaClave() throws Exception {
        long evento = crearEvento("Circo", "12000", 50);
        String usuario = usuarioNuevo("eva");
        String json = generarTicket(crearCompra(evento, usuario, 1));
        long id = id(json);
        String clave = JsonPath.read(json, "$.s3Key");
        String checksumAnterior = JsonPath.read(json, "$.checksumSha256");
        int objetosAntes = s3.objetos().size();

        String r = mvc.perform(put("/api/tickets/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":4,\"nombreAsistente\":\"Eva Soto\",\"correoAsistente\":\"eva@x.cl\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.s3Key").value(clave))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.cantidad").value(4))
                .andExpect(jsonPath("$.precioTotal").value(48000))
                .andExpect(jsonPath("$.nombreAsistente").value("Eva Soto"))
                .andExpect(jsonPath("$.fechaModificacion").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(s3.objetos()).hasSize(objetosAntes).containsKey(clave);
        byte[] nuevo = s3.objetos().get(clave).datos();
        assertThat(sha256(nuevo)).isEqualTo(JsonPath.read(r, "$.checksumSha256")).isNotEqualTo(checksumAnterior);
        assertThat(Files.readAllBytes(efs().resolve("tickets").resolve(clave))).isEqualTo(nuevo);
        assertThat(textoPdf(nuevo)).contains("Eva Soto", "Cantidad de entradas 4", "Total pagado $48.000",
                "versión 2");
        assertThat(s3.objetos().get(clave).metadatos()).containsEntry("version", "2");
    }

    @Test
    @DisplayName("Modificar evento o usuario se rechaza (400); también un cuerpo vacío o una cantidad sin cupo")
    void modificacionesNoPermitidas() throws Exception {
        long evento = crearEvento("Danza", "8000", 3);
        long id = id(generarTicket(crearCompra(evento, usuarioNuevo("fer"), 1)));
        mvc.perform(put("/api/tickets/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"eventoId\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("evento ni el usuario")));
        mvc.perform(put("/api/tickets/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"otro\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/tickets/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/tickets/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":4}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/tickets/99999").contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Eliminar valida que exista: borra de S3 y EFS; un ticket inexistente o ya eliminado da 404")
    void eliminaValidandoExistencia() throws Exception {
        String json = generarTicket(crearCompra(crearEvento("Magia", "7000", 10), usuarioNuevo("gabo"), 1));
        long id = id(json);
        String clave = JsonPath.read(json, "$.s3Key");
        Path enEfs = efs().resolve("tickets").resolve(clave);

        mvc.perform(delete("/api/tickets/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ELIMINADO"))
                .andExpect(jsonPath("$.fechaEliminacion").exists())
                .andExpect(jsonPath("$.aviso", containsString("Eliminado de S3")));
        assertThat(s3.objetos()).doesNotContainKey(clave);
        assertThat(enEfs).doesNotExist();

        mvc.perform(delete("/api/tickets/" + id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/tickets/99999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe el ticket con id 99999"));
        mvc.perform(get("/api/tickets/" + id + "/descargar")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Historial con filtros combinables por usuario, evento, estado y rango de fechas")
    void historialConFiltros() throws Exception {
        long evento1 = crearEvento("Historial A", "1000", 100);
        long evento2 = crearEvento("Historial B", "1000", 100);
        String hugo = usuarioNuevo("hugo");
        String ines = usuarioNuevo("ines");
        generarTicket(crearCompra(evento1, hugo, 1));
        long eliminado = id(generarTicket(crearCompra(evento2, hugo, 1)));
        generarTicket(crearCompra(evento1, ines, 1));
        mvc.perform(delete("/api/tickets/" + eliminado)).andExpect(status().isOk());

        mvc.perform(get("/api/tickets/historial").param("usuario", hugo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.filtros.usuario").value(hugo))
                .andExpect(jsonPath("$.tickets[*].usuario", everyItem(is(hugo))));
        mvc.perform(get("/api/tickets/historial").param("eventoId", String.valueOf(evento1)))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/tickets/historial").param("usuario", hugo).param("estado", "eliminado"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.tickets[0].id").value((int) eliminado));
        mvc.perform(get("/api/tickets/historial").param("usuario", hugo)
                        .param("eventoId", String.valueOf(evento1)).param("estado", "SUBIDO"))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/tickets/historial").param("usuario", hugo)
                        .param("desde", "2020-01-01").param("hasta", "2020-12-31"))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/tickets/historial").param("usuario", ines)
                        .param("desde", java.time.LocalDate.now(java.time.ZoneId.of("America/Santiago")).toString())
                        .param("hasta", java.time.LocalDate.now(java.time.ZoneId.of("America/Santiago")).toString()))
                .andExpect(jsonPath("$.total").value(1));

        mvc.perform(get("/api/tickets/historial").param("estado", "PERDIDO")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/tickets/historial").param("desde", "2026-12-01").param("hasta", "2026-01-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tickets/historial").param("desde", "01-12-2026")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Estadísticas por evento: reservas, ventas, entradas, recaudación, usuarios y ocupación")
    void estadisticasPorEvento() throws Exception {
        long evento = crearEvento("Estadísticas", "10000", 20);
        String ana = usuarioNuevo("ana");
        String beto = usuarioNuevo("beto");
        generarTicket(crearCompra(evento, ana, 2));
        long ticketBeto = id(generarTicket(crearCompra(evento, beto, 3)));
        crearCompra(evento, beto, 1);
        long otra = crearCompra(evento, ana, 1);
        mvc.perform(delete("/api/tickets/" + id(generarTicket(otra)))).andExpect(status().isOk());
        mvc.perform(put("/api/tickets/" + ticketBeto).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cantidad\":4}")).andExpect(status().isOk());

        mvc.perform(get("/api/eventos/" + evento + "/estadisticas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservas").value(4))
                .andExpect(jsonPath("$.entradasReservadas").value(8))
                .andExpect(jsonPath("$.ventas").value(2))
                .andExpect(jsonPath("$.entradasVendidas").value(6))
                .andExpect(jsonPath("$.entradasSinTicket").value(2))
                .andExpect(jsonPath("$.entradasDisponibles").value(12))
                .andExpect(jsonPath("$.ocupacionPorcentaje").value(40.0))
                .andExpect(jsonPath("$.recaudacion").value(60000))
                .andExpect(jsonPath("$.usuariosDistintos").value(2))
                .andExpect(jsonPath("$.ticketsEliminados").value(1));
        mvc.perform(get("/api/eventos/estadisticas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventoId", hasItem((int) evento)));
        mvc.perform(get("/api/eventos/99999/estadisticas")).andExpect(status().isNotFound());
    }

    private static Map<String, byte[]> zip(byte[] datos) throws Exception {
        Map<String, byte[]> entradas = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(datos))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                entradas.put(e.getName(), zis.readAllBytes());
            }
        }
        return entradas;
    }
}

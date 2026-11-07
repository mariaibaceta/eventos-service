package com.duoc.eventos_service;

import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PruebaBase.S3DePrueba.class)
public abstract class PruebaBase {

    /** Carpeta que simula el EFS; es una sola para todo el contexto de Spring compartido entre clases. */
    private static final Path efsTemporal = crearCarpeta();

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected FakeS3Client s3;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("app.efs.base-path", () -> efsTemporal.toString());
    }

    @TestConfiguration
    static class S3DePrueba {
        @Bean
        @Primary
        FakeS3Client fakeS3Client() {
            return new FakeS3Client();
        }
    }

    @BeforeEach
    void reiniciarS3() {
        s3.reiniciar();
    }

    private static Path crearCarpeta() {
        try {
            return java.nio.file.Files.createTempDirectory("efs-pruebas-");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected static Path efs() {
        return efsTemporal;
    }

    /** Usuario único por prueba, para que los datos de una prueba no afecten a otra. */
    protected static String usuarioNuevo(String base) {
        return base + SECUENCIA.incrementAndGet();
    }

    protected long crearEvento(String nombre, String precio, int capacidad) throws Exception {
        String json = """
                {"nombre":"%s","fecha":"2026-12-12T21:00:00","lugar":"Movistar Arena","precio":%s,"capacidad":%d}
                """.formatted(nombre, precio, capacidad);
        String r = mvc.perform(post("/api/eventos").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    protected long crearCompra(long eventoId, String usuario, int cantidad) throws Exception {
        String json = """
                {"eventoId":%d,"usuario":"%s","cantidad":%d}""".formatted(eventoId, usuario, cantidad);
        String r = mvc.perform(post("/api/compras").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    protected String generarTicket(long compraId) throws Exception {
        String json = """
                {"compraId":%d,"nombreAsistente":"Ana Pérez","correoAsistente":"ana@correo.cl"}""".formatted(compraId);
        return mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    protected static String textoPdf(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    protected static long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}

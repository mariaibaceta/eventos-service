package com.duoc.eventos_service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Endpoints genéricos de S2 con las correcciones del docente: reemplazo en la misma clave y borrado validado. */
class S3GenericoTest extends PruebaBase {

    private static final String RUTA = "/s3/" + FakeS3Client.BUCKET + "/object";

    private static MockMultipartFile archivo(String contenido) {
        return new MockMultipartFile("file", "resumen.txt", "text/plain", contenido.getBytes());
    }

    @Test
    @DisplayName("Subir, reemplazar en la misma clave, listar por carpeta, descargar y eliminar")
    void cicloCompleto() throws Exception {
        String clave = "resumenes/15/resumen_15.txt";
        mvc.perform(multipart(RUTA).file(archivo("v1")).param("key", clave)).andExpect(status().isCreated());
        mvc.perform(multipart(RUTA).file(archivo("otra")).param("key", clave)).andExpect(status().isConflict());

        mvc.perform(multipart(RUTA).file(archivo("v2")).param("key", clave).with(r -> {
            r.setMethod("PUT");
            return r;
        })).andExpect(status().isNoContent());
        assertThat(s3.objetos()).hasSize(1).containsKey(clave);
        assertThat(new String(s3.objetos().get(clave).datos())).isEqualTo("v2");

        mvc.perform(get("/s3/" + FakeS3Client.BUCKET + "/objects").param("prefijo", "resumenes/15/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].key").value(clave));
        mvc.perform(get(RUTA).param("key", clave))
                .andExpect(status().isOk())
                .andExpect(content().string("v2"))
                .andExpect(header().exists("X-Checksum-SHA256"));

        mvc.perform(delete(RUTA).param("key", clave)).andExpect(status().isNoContent());
        assertThat(s3.objetos()).isEmpty();
    }

    @Test
    @DisplayName("Eliminar o reemplazar un archivo inexistente responde 404 (validación previa de existencia)")
    void inexistente404() throws Exception {
        mvc.perform(delete(RUTA).param("key", "no/existe.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje", containsString("no/existe.txt")));
        mvc.perform(multipart(RUTA).file(archivo("x")).param("key", "no/existe.txt").with(r -> {
            r.setMethod("PUT");
            return r;
        })).andExpect(status().isNotFound());
        mvc.perform(get(RUTA).param("key", "no/existe.txt")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Bucket inexistente da 404; archivo vacío o clave inválida dan 400")
    void validaciones() throws Exception {
        mvc.perform(get("/s3/otro-bucket/objects")).andExpect(status().isNotFound());
        mvc.perform(multipart(RUTA).file(archivo("")).param("key", "a.txt")).andExpect(status().isBadRequest());
        mvc.perform(multipart(RUTA).file(archivo("x")).param("key", "/a.txt")).andExpect(status().isBadRequest());
        mvc.perform(multipart(RUTA).file(archivo("x"))).andExpect(status().isBadRequest());
    }
}

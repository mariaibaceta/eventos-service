package com.duoc.eventos_service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EventosYComprasTest extends PruebaBase {

    @Test
    @DisplayName("Crear y listar eventos con nombre, fecha y precio")
    void creaYListaEventos() throws Exception {
        long id = crearEvento("Festival Andino", "25000", 100);
        mvc.perform(get("/api/eventos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Festival Andino"))
                .andExpect(jsonPath("$.fecha").value("2026-12-12T21:00:00"))
                .andExpect(jsonPath("$.precio").value(25000))
                .andExpect(jsonPath("$.entradasDisponibles").value(100));
        mvc.perform(get("/api/eventos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) id)));
    }

    @Test
    @DisplayName("Evento inválido responde 400 con el detalle de cada campo")
    void eventoInvalido() throws Exception {
        mvc.perform(post("/api/eventos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"precio\":-5,\"capacidad\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasSize(4)))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("fecha:"))));
        mvc.perform(post("/api/eventos").contentType(MediaType.APPLICATION_JSON).content("{malformado"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("La compra calcula el total con el precio del evento y descuenta disponibilidad")
    void compraCalculaTotal() throws Exception {
        long evento = crearEvento("Teatro Municipal", "45990", 50);
        String usuario = usuarioNuevo("carla");
        mvc.perform(post("/api/compras").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventoId\":%d,\"usuario\":\"%s\",\"cantidad\":3}".formatted(evento,
                                usuario.toUpperCase())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/compras/")))
                .andExpect(jsonPath("$.usuario").value(usuario))
                .andExpect(jsonPath("$.cantidad").value(3))
                .andExpect(jsonPath("$.precioUnitario").value(45990))
                .andExpect(jsonPath("$.precioTotal").value(137970))
                .andExpect(jsonPath("$.fechaCompra").exists());
        mvc.perform(get("/api/eventos/" + evento)).andExpect(jsonPath("$.entradasDisponibles").value(47));
    }

    @Test
    @DisplayName("No se venden más entradas que la capacidad (409) y el evento debe existir (404)")
    void capacidadYEventoInexistente() throws Exception {
        long evento = crearEvento("Show íntimo", "10000", 5);
        crearCompra(evento, usuarioNuevo("luis"), 4);
        mvc.perform(post("/api/compras").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventoId\":%d,\"usuario\":\"pedro\",\"cantidad\":2}".formatted(evento)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("disponibles 1")));
        mvc.perform(post("/api/compras").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventoId\":99999,\"usuario\":\"pedro\",\"cantidad\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Usuario con caracteres no permitidos para una carpeta se rechaza (400)")
    void usuarioInvalido() throws Exception {
        long evento = crearEvento("Charla", "1000", 10);
        for (String usuario : new String[]{"../etc", "a/b", "ab", "con espacio"}) {
            mvc.perform(post("/api/compras").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"eventoId\":%d,\"usuario\":\"%s\",\"cantidad\":1}".formatted(evento, usuario)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("El resumen de compra es un PDF con los datos reales: entradas, precio unitario y total")
    void resumenConDatosReales() throws Exception {
        long evento = crearEvento("Concierto Sinfónico", "45990", 100);
        long compra = crearCompra(evento, usuarioNuevo("marta"), 3);
        for (String ruta : new String[]{"/api/compras/" + compra + "/resumen", "/api/resumen/descargar/" + compra}) {
            byte[] pdf = mvc.perform(get(ruta))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/pdf"))
                    .andExpect(header().string("Content-Disposition", containsString("resumen_compra_" + compra)))
                    .andReturn().getResponse().getContentAsByteArray();
            String texto = textoPdf(pdf);
            assertThat(texto).contains("Concierto Sinfónico", "Número de entradas 3", "Precio unitario $45.990",
                    "Precio total $137.970", "Compra N.º " + compra);
        }
        mvc.perform(get("/api/compras/99999/resumen")).andExpect(status().isNotFound());
    }
}

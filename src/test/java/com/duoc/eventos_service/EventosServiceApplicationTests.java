package com.duoc.eventos_service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EventosServiceApplicationTests extends PruebaBase {

    @Test
    @DisplayName("El contexto levanta sin AWS ni base de datos externa y expone el health check")
    void contextoYHealth() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/no-existe")).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }
}

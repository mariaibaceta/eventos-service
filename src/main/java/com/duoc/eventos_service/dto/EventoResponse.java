package com.duoc.eventos_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EventoResponse(Long id, String nombre, LocalDateTime fecha, String lugar, BigDecimal precio,
                             Integer capacidad, long entradasDisponibles) {
}

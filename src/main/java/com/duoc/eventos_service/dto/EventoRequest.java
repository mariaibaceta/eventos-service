package com.duoc.eventos_service.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EventoRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre admite hasta 120 caracteres")
        String nombre,

        @NotNull(message = "La fecha es obligatoria (formato 2026-12-31T20:00:00)")
        LocalDateTime fecha,

        @Size(max = 120, message = "El lugar admite hasta 120 caracteres")
        String lugar,

        @NotNull(message = "El precio es obligatorio")
        @Positive(message = "El precio debe ser mayor que 0")
        @Digits(integer = 10, fraction = 2, message = "El precio admite hasta 10 enteros y 2 decimales")
        BigDecimal precio,

        @NotNull(message = "La capacidad es obligatoria")
        @Min(value = 1, message = "La capacidad debe ser al menos 1")
        @Max(value = 100000, message = "La capacidad no puede superar 100000")
        Integer capacidad) {
}

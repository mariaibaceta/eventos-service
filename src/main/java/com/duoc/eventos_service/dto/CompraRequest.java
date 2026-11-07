package com.duoc.eventos_service.dto;

import jakarta.validation.constraints.*;

public record CompraRequest(
        @NotNull(message = "El eventoId es obligatorio")
        Long eventoId,

        @NotBlank(message = "El usuario es obligatorio")
        @Pattern(regexp = Validaciones.USUARIO, message = Validaciones.USUARIO_MENSAJE)
        String usuario,

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser al menos 1")
        @Max(value = 20, message = "Se pueden reservar hasta 20 entradas por compra")
        Integer cantidad) {
}

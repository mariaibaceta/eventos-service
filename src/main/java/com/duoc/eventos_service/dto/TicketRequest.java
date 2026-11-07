package com.duoc.eventos_service.dto;

import jakarta.validation.constraints.*;

public record TicketRequest(
        @NotNull(message = "El compraId es obligatorio")
        Long compraId,

        @NotBlank(message = "El nombre del asistente es obligatorio")
        @Size(max = 80, message = "El nombre del asistente admite hasta 80 caracteres")
        String nombreAsistente,

        @NotBlank(message = "El correo del asistente es obligatorio")
        @Email(message = "El correo del asistente no es válido")
        @Size(max = 120, message = "El correo admite hasta 120 caracteres")
        String correoAsistente) {
}

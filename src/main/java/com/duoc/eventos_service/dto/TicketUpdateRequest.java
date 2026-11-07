package com.duoc.eventos_service.dto;

import jakarta.validation.constraints.*;

/**
 * Datos modificables de un ticket. eventoId y usuario se reciben solo para rechazarlos:
 * cambiarlos movería el archivo a otra carpeta de S3, y la modificación debe reemplazarlo en la misma ruta.
 */
public record TicketUpdateRequest(
        @Min(value = 1, message = "La cantidad debe ser al menos 1")
        @Max(value = 20, message = "Se pueden reservar hasta 20 entradas por compra")
        Integer cantidad,

        @Size(min = 1, max = 80, message = "El nombre del asistente debe tener entre 1 y 80 caracteres")
        String nombreAsistente,

        @Email(message = "El correo del asistente no es válido")
        @Size(max = 120, message = "El correo admite hasta 120 caracteres")
        String correoAsistente,

        Long eventoId,
        String usuario) {
}

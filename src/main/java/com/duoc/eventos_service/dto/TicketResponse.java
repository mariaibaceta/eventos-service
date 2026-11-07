package com.duoc.eventos_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TicketResponse(Long id, String numero, String codigo, Long compraId, Long eventoId, String evento,
                             String usuario, String nombreAsistente, String correoAsistente, Integer cantidad,
                             BigDecimal precioUnitario, BigDecimal precioTotal, String estado, Integer version,
                             String s3Key, String rutaEfs, String checksumSha256, Long tamanoBytes,
                             LocalDateTime fechaCreacion, LocalDateTime fechaModificacion,
                             LocalDateTime fechaSubida, LocalDateTime fechaEliminacion, String aviso) {
}

package com.duoc.eventos_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Resumen de la compra: evento, número de entradas, precio unitario y total. */
public record CompraResponse(Long id, Long eventoId, String evento, String usuario, Integer cantidad,
                             BigDecimal precioUnitario, BigDecimal precioTotal, LocalDateTime fechaCompra) {
}

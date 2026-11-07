package com.duoc.eventos_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Reserva = compra registrada (entradas apartadas). Venta = compra con ticket emitido y vigente.
 */
public record EstadisticasEventoResponse(Long eventoId, String evento, LocalDateTime fecha, Integer capacidad,
                                         long reservas, long entradasReservadas, long ventas,
                                         long entradasVendidas, long entradasSinTicket, long entradasDisponibles,
                                         BigDecimal ocupacionPorcentaje, BigDecimal recaudacion,
                                         long usuariosDistintos, long ticketsEliminados) {
}

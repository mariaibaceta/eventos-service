package com.duoc.eventos_service.dto;

import java.math.BigDecimal;

/** Agregados de tickets emitidos (ventas) de un evento. */
public record TotalesVentas(long ventas, long entradas, BigDecimal recaudacion) {

    public TotalesVentas(Long ventas, Long entradas, BigDecimal recaudacion) {
        this(ventas == null ? 0 : ventas, entradas == null ? 0 : entradas,
                recaudacion == null ? BigDecimal.ZERO : recaudacion);
    }
}

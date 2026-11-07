package com.duoc.eventos_service.dto;

/** Agregados de compras (reservas) de un evento. */
public record TotalesReservas(long reservas, long entradas, long usuarios) {

    public TotalesReservas(Long reservas, Long entradas, Long usuarios) {
        this(valor(reservas), valor(entradas), valor(usuarios));
    }

    private static long valor(Long numero) {
        return numero == null ? 0 : numero;
    }
}

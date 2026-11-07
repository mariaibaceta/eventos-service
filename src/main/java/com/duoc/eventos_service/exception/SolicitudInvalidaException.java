package com.duoc.eventos_service.exception;

/** 400: la solicitud no cumple una regla de negocio. */
public class SolicitudInvalidaException extends RuntimeException {
    public SolicitudInvalidaException(String message) {
        super(message);
    }

    public SolicitudInvalidaException(String message, Throwable cause) {
        super(message, cause);
    }
}

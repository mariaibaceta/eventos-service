package com.duoc.eventos_service.exception;

/** 409: la operación choca con el estado actual (capacidad, ticket ya emitido, etc.). */
public class ConflictoException extends RuntimeException {
    public ConflictoException(String message) {
        super(message);
    }

    public ConflictoException(String message, Throwable cause) {
        super(message, cause);
    }
}

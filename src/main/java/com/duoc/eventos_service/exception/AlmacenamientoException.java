package com.duoc.eventos_service.exception;

/** 500: error al leer o escribir en EFS o S3. */
public class AlmacenamientoException extends RuntimeException {
    public AlmacenamientoException(String message) {
        super(message);
    }

    public AlmacenamientoException(String message, Throwable cause) {
        super(message, cause);
    }
}

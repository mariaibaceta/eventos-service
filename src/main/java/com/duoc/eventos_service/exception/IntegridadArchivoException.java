package com.duoc.eventos_service.exception;

/** 502: el archivo descargado no coincide con el checksum SHA-256 registrado. */
public class IntegridadArchivoException extends RuntimeException {
    public IntegridadArchivoException(String message) {
        super(message);
    }

    public IntegridadArchivoException(String message, Throwable cause) {
        super(message, cause);
    }
}

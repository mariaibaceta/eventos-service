package com.duoc.eventos_service.exception;

/** 403: AWS rechazó la operación (credenciales vencidas o sin permisos). */
public class S3AccesoDenegadoException extends RuntimeException {
    public S3AccesoDenegadoException(String message) {
        super(message);
    }

    public S3AccesoDenegadoException(String message, Throwable cause) {
        super(message, cause);
    }
}

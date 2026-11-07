package com.duoc.eventos_service.dto;

/** Reglas compartidas por los DTO. El usuario forma parte de la ruta en S3 y en EFS. */
public final class Validaciones {

    public static final String USUARIO = "^[A-Za-z0-9][A-Za-z0-9._-]{2,39}$";
    public static final String USUARIO_MENSAJE =
            "El usuario debe tener entre 3 y 40 caracteres: letras, números, punto, guion o guion bajo";

    private Validaciones() {
    }
}

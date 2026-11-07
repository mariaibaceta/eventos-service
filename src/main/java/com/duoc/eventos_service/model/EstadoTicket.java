package com.duoc.eventos_service.model;

public enum EstadoTicket {
    /** El PDF existe en EFS, pero todavía no está en S3. */
    GENERADO,
    /** El PDF está en EFS y en S3. */
    SUBIDO,
    /** Los archivos se borraron de EFS y S3; el registro queda para el historial. */
    ELIMINADO
}

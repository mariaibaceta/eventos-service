package com.duoc.eventos_service.controller;

import com.duoc.eventos_service.service.ArchivoDescarga;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/** Respuesta de descarga con nombre de archivo, tipo de contenido y checksum para verificar la integridad. */
final class Descargas {

    static final String CABECERA_CHECKSUM = "X-Checksum-SHA256";

    private Descargas() {
    }

    static ResponseEntity<byte[]> archivo(ArchivoDescarga archivo) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.nombre(), StandardCharsets.UTF_8).build()
                                .toString())
                .header(CABECERA_CHECKSUM, archivo.checksumSha256())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, CABECERA_CHECKSUM)
                .contentType(MediaType.parseMediaType(archivo.contentType()))
                .contentLength(archivo.datos().length)
                .body(archivo.datos());
    }
}

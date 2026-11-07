package com.duoc.eventos_service.service;

/** Archivo listo para enviar al cliente, con su checksum SHA-256 ya verificado. */
public record ArchivoDescarga(String nombre, String contentType, byte[] datos, String checksumSha256) {
}

package com.duoc.eventos_service.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

public final class Checksums {

    private Checksums() {
    }

    public static byte[] sha256(byte[] datos) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(datos);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public static String sha256Hex(byte[] datos) {
        return HexFormat.of().formatHex(sha256(datos));
    }

    /** Formato que exige S3 en el encabezado x-amz-checksum-sha256. */
    public static String sha256Base64(byte[] datos) {
        return Base64.getEncoder().encodeToString(sha256(datos));
    }
}

package com.duoc.eventos_service;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/** S3 en memoria para las pruebas: un solo bucket, con opción de simular caída o corrupción. */
public class FakeS3Client implements S3Client {

    public static final String BUCKET = "bucket-pruebas";

    public record Objeto(byte[] datos, String contentType, Map<String, String> metadatos, Instant modificado) {
    }

    private final Map<String, Objeto> objetos = new ConcurrentSkipListMap<>();
    private volatile boolean caido;

    public void reiniciar() {
        objetos.clear();
        caido = false;
    }

    public void setCaido(boolean caido) {
        this.caido = caido;
    }

    public Map<String, Objeto> objetos() {
        return objetos;
    }

    public void corromper(String clave) {
        Objeto o = objetos.get(clave);
        objetos.put(clave, new Objeto("alterado".getBytes(), o.contentType(), o.metadatos(), Instant.now()));
    }

    private void validar(String bucket) {
        if (caido) {
            throw SdkClientException.create("Sin conexión simulada");
        }
        if (!BUCKET.equals(bucket)) {
            throw NoSuchBucketException.builder().statusCode(404).message("NoSuchBucket").build();
        }
    }

    @Override
    public PutObjectResponse putObject(PutObjectRequest req, RequestBody body) {
        validar(req.bucket());
        try (InputStream in = body.contentStreamProvider().newStream()) {
            objetos.put(req.key(), new Objeto(in.readAllBytes(), req.contentType(), req.metadata(), Instant.now()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return PutObjectResponse.builder().build();
    }

    @Override
    public ResponseBytes<GetObjectResponse> getObjectAsBytes(GetObjectRequest req) {
        validar(req.bucket());
        Objeto o = objetos.get(req.key());
        if (o == null) {
            throw NoSuchKeyException.builder().statusCode(404).message("NoSuchKey").build();
        }
        return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), o.datos());
    }

    @Override
    public HeadObjectResponse headObject(HeadObjectRequest req) {
        validar(req.bucket());
        Objeto o = objetos.get(req.key());
        if (o == null) {
            throw NoSuchKeyException.builder().statusCode(404).message("Not Found").build();
        }
        return HeadObjectResponse.builder().contentLength((long) o.datos().length).build();
    }

    @Override
    public DeleteObjectResponse deleteObject(DeleteObjectRequest req) {
        validar(req.bucket());
        objetos.remove(req.key());
        return DeleteObjectResponse.builder().build();
    }

    @Override
    public ListObjectsV2Response listObjectsV2(ListObjectsV2Request req) {
        validar(req.bucket());
        String prefijo = req.prefix() == null ? "" : req.prefix();
        return ListObjectsV2Response.builder().isTruncated(false).contents(objetos.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefijo))
                .map(e -> S3Object.builder().key(e.getKey()).size((long) e.getValue().datos().length)
                        .lastModified(e.getValue().modificado()).build())
                .toList()).build();
    }

    @Override
    public String serviceName() {
        return "s3";
    }

    @Override
    public void close() {
    }
}

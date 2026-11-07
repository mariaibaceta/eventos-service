package com.duoc.eventos_service.service;

import com.duoc.eventos_service.dto.S3ObjectDto;
import com.duoc.eventos_service.exception.AlmacenamientoException;
import com.duoc.eventos_service.exception.ConflictoException;
import com.duoc.eventos_service.exception.RecursoNoEncontradoException;
import com.duoc.eventos_service.exception.S3AccesoDenegadoException;
import com.duoc.eventos_service.exception.SolicitudInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Operaciones sobre S3. Toda eliminación o reemplazo valida antes que el objeto exista. */
@Service
public class AwsS3Service {

    private static final Logger LOG = LoggerFactory.getLogger(AwsS3Service.class);

    private final S3Client s3;
    private final String bucketTickets;

    public AwsS3Service(S3Client s3, @Value("${app.s3.bucket:}") String bucketTickets) {
        this.s3 = s3;
        this.bucketTickets = bucketTickets;
    }

    public String bucketTickets() {
        if (bucketTickets == null || bucketTickets.isBlank()) {
            throw new AlmacenamientoException("No está configurado el bucket S3 (variable S3_BUCKET)");
        }
        return bucketTickets;
    }

    public List<S3ObjectDto> listar(String bucket, String prefijo) {
        return ejecutar(bucket, null, () -> {
            ListObjectsV2Request.Builder solicitud = ListObjectsV2Request.builder().bucket(bucket);
            if (prefijo != null && !prefijo.isBlank()) {
                solicitud.prefix(prefijo);
            }
            return s3.listObjectsV2Paginator(solicitud.build()).contents().stream()
                    .map(o -> new S3ObjectDto(o.key(), o.size(),
                            o.lastModified() == null ? null : o.lastModified().toString()))
                    .toList();
        });
    }

    public boolean existe(String bucket, String clave) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(clave).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404 && !"NoSuchBucket".equals(codigo(e))) {
                return false;
            }
            throw traducir(e, bucket, clave);
        } catch (SdkClientException e) {
            throw sinConexion(e);
        }
    }

    public byte[] descargar(String bucket, String clave) {
        return ejecutar(bucket, clave, () -> {
            byte[] datos = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(clave).build())
                    .asByteArray();
            LOG.info("Descargado de S3: s3://{}/{} ({} bytes)", bucket, clave, datos.length);
            return datos;
        });
    }

    /**
     * Sube bytes a una clave fija. Si la clave existe, S3 reemplaza el objeto en la misma ruta.
     * El checksum SHA-256 viaja en la solicitud y S3 lo verifica al recibir el archivo.
     */
    public void subir(String bucket, String clave, byte[] datos, String contentType, Map<String, String> metadatos) {
        ejecutar(bucket, clave, () -> {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(clave).contentType(contentType)
                    .contentLength((long) datos.length).metadata(metadatos)
                    .checksumSHA256(Checksums.sha256Base64(datos)).build(), RequestBody.fromBytes(datos));
            LOG.info("Subido a S3: s3://{}/{} ({} bytes)", bucket, clave, datos.length);
            return null;
        });
    }

    /** Crea un objeto nuevo desde un archivo multipart; si la clave ya existe responde 409 (usar PUT). */
    public void crear(String bucket, String clave, MultipartFile archivo) {
        validarClave(clave);
        byte[] datos = leerArchivo(archivo);
        if (existe(bucket, clave)) {
            throw new ConflictoException("Ya existe un objeto con la clave '" + clave + "'. Use PUT para reemplazarlo");
        }
        subir(bucket, clave, datos, tipo(archivo), Map.of());
    }

    /** Reemplaza el contenido de un objeto existente sin cambiar su carpeta ni su nombre. */
    public void reemplazar(String bucket, String clave, MultipartFile archivo) {
        validarClave(clave);
        byte[] datos = leerArchivo(archivo);
        if (!existe(bucket, clave)) {
            throw new RecursoNoEncontradoException("No existe el objeto '" + clave + "' en el bucket " + bucket);
        }
        subir(bucket, clave, datos, tipo(archivo), Map.of());
    }

    /** Elimina un objeto validando primero que exista (404 si no existe). */
    public void eliminar(String bucket, String clave) {
        validarClave(clave);
        if (!existe(bucket, clave)) {
            throw new RecursoNoEncontradoException("No existe el objeto '" + clave + "' en el bucket " + bucket);
        }
        ejecutar(bucket, clave, () -> {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(clave).build());
            LOG.info("Eliminado de S3: s3://{}/{}", bucket, clave);
            return null;
        });
    }

    private <T> T ejecutar(String bucket, String clave, Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (S3Exception e) {
            throw traducir(e, bucket, clave);
        } catch (SdkClientException e) {
            throw sinConexion(e);
        }
    }

    private static RuntimeException traducir(S3Exception e, String bucket, String clave) {
        String codigo = codigo(e);
        if (e instanceof NoSuchBucketException || "NoSuchBucket".equals(codigo)) {
            return new RecursoNoEncontradoException("No existe el bucket: " + bucket);
        }
        if (e instanceof NoSuchKeyException || e.statusCode() == 404) {
            return new RecursoNoEncontradoException("No existe el objeto '" + clave + "' en el bucket " + bucket);
        }
        if (e.statusCode() == 403) {
            return new S3AccesoDenegadoException("AWS rechazó el acceso al bucket " + bucket
                    + " (credenciales vencidas o sin permisos)", e);
        }
        return new AlmacenamientoException("Error de S3 (" + e.statusCode() + " " + codigo + ")", e);
    }

    private static String codigo(S3Exception e) {
        return e.awsErrorDetails() == null ? null : e.awsErrorDetails().errorCode();
    }

    private static AlmacenamientoException sinConexion(SdkClientException e) {
        return new AlmacenamientoException("No fue posible conectar con S3 (revise credenciales y región)", e);
    }

    private static void validarClave(String clave) {
        if (clave == null || clave.isBlank() || clave.length() > 1024 || clave.startsWith("/")) {
            throw new SolicitudInvalidaException("La clave del objeto es obligatoria, no debe empezar con '/' "
                    + "y admite hasta 1024 caracteres");
        }
    }

    private static byte[] leerArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new SolicitudInvalidaException("El archivo está vacío o no se envió");
        }
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new AlmacenamientoException("No fue posible leer el archivo recibido", e);
        }
    }

    private static String tipo(MultipartFile archivo) {
        return archivo.getContentType() == null ? "application/octet-stream" : archivo.getContentType();
    }
}

package com.duoc.eventos_service.controller;

import com.duoc.eventos_service.dto.S3ObjectDto;
import com.duoc.eventos_service.service.ArchivoDescarga;
import com.duoc.eventos_service.service.AwsS3Service;
import com.duoc.eventos_service.service.Checksums;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Operaciones genéricas sobre un bucket (trabajo de S2, con las correcciones del docente). */
@RestController
@RequestMapping("/s3/{bucket}")
public class AwsS3Controller {

    private final AwsS3Service servicio;

    public AwsS3Controller(AwsS3Service servicio) {
        this.servicio = servicio;
    }

    /** Lista los objetos del bucket; con 'prefijo' muestra una carpeta, por ejemplo evento1/ana/. */
    @GetMapping("/objects")
    public List<S3ObjectDto> listar(@PathVariable String bucket, @RequestParam(required = false) String prefijo) {
        return servicio.listar(bucket, prefijo);
    }

    @GetMapping("/object")
    public ResponseEntity<byte[]> descargar(@PathVariable String bucket, @RequestParam String key) {
        byte[] datos = servicio.descargar(bucket, key);
        String nombre = key.substring(key.lastIndexOf('/') + 1);
        return Descargas.archivo(new ArchivoDescarga(nombre, "application/octet-stream", datos,
                Checksums.sha256Hex(datos)));
    }

    /** Sube un archivo nuevo; si la clave ya existe responde 409. */
    @PostMapping("/object")
    public ResponseEntity<Void> subir(@PathVariable String bucket, @RequestParam String key,
                                      @RequestParam("file") MultipartFile file) {
        servicio.crear(bucket, key, file);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** Reemplaza el archivo en la misma carpeta y con el mismo nombre (404 si no existe). */
    @PutMapping("/object")
    public ResponseEntity<Void> reemplazar(@PathVariable String bucket, @RequestParam String key,
                                           @RequestParam("file") MultipartFile file) {
        servicio.reemplazar(bucket, key, file);
        return ResponseEntity.noContent().build();
    }

    /** Elimina el archivo validando antes que exista (404 si no existe). */
    @DeleteMapping("/object")
    public ResponseEntity<Void> eliminar(@PathVariable String bucket, @RequestParam String key) {
        servicio.eliminar(bucket, key);
        return ResponseEntity.noContent().build();
    }
}

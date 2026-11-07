package com.duoc.eventos_service.service;

import com.duoc.eventos_service.exception.AlmacenamientoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Almacenamiento temporal en EFS. En la EC2, el EFS está montado en /mnt/efs y el contenedor lo ve en /app/efs.
 * Los tickets se guardan en {base}/tickets/ con la misma estructura de carpetas que en S3.
 */
@Service
public class EfsStorageService {

    private static final Logger LOG = LoggerFactory.getLogger(EfsStorageService.class);

    private final Path base;

    public EfsStorageService(@Value("${app.efs.base-path}") String basePath) {
        this.base = Path.of(basePath).toAbsolutePath().normalize();
    }

    public Path rutaTicket(String claveRelativa) {
        Path ruta = base.resolve("tickets").resolve(claveRelativa).normalize();
        if (!ruta.startsWith(base)) {
            throw new AlmacenamientoException("Ruta fuera del directorio EFS: " + claveRelativa);
        }
        return ruta;
    }

    /** Escritura atómica: se escribe un temporal y luego se reemplaza el archivo final. */
    public void escribir(Path ruta, byte[] datos) {
        try {
            Files.createDirectories(ruta.getParent());
            Path temporal = Files.createTempFile(ruta.getParent(), ".tmp-", ".pdf");
            Files.write(temporal, datos);
            Files.move(temporal, ruta, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            permisosLectura(ruta);
            LOG.info("Archivo guardado en EFS: {} ({} bytes)", ruta, datos.length);
        } catch (IOException e) {
            throw new AlmacenamientoException("No fue posible escribir el archivo en EFS: " + ruta, e);
        }
    }

    /** rw-r--r--: el archivo se puede revisar desde la EC2 sin sudo. */
    private static void permisosLectura(Path ruta) {
        try {
            Files.setPosixFilePermissions(ruta, PosixFilePermissions.fromString("rw-r--r--"));
        } catch (UnsupportedOperationException | IOException e) {
            LOG.debug("No se pudieron ajustar los permisos de {}", ruta);
        }
    }

    public byte[] leer(Path ruta) {
        try {
            return Files.readAllBytes(ruta);
        } catch (IOException e) {
            throw new AlmacenamientoException("No fue posible leer el archivo desde EFS: " + ruta, e);
        }
    }

    public boolean existe(Path ruta) {
        return Files.isRegularFile(ruta);
    }

    public boolean eliminar(Path ruta) {
        try {
            boolean borrado = Files.deleteIfExists(ruta);
            if (borrado) {
                LOG.info("Archivo eliminado de EFS: {}", ruta);
            }
            return borrado;
        } catch (IOException e) {
            throw new AlmacenamientoException("No fue posible eliminar el archivo de EFS: " + ruta, e);
        }
    }
}

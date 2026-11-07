package com.duoc.eventos_service.controller;

import com.duoc.eventos_service.dto.CompraRequest;
import com.duoc.eventos_service.dto.CompraResponse;
import com.duoc.eventos_service.service.ArchivoDescarga;
import com.duoc.eventos_service.service.Checksums;
import com.duoc.eventos_service.service.CompraService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class CompraController {

    private final CompraService servicio;

    public CompraController(CompraService servicio) {
        this.servicio = servicio;
    }

    /** Registra la compra (reserva) y responde con el resumen: evento, entradas, precio unitario y total. */
    @PostMapping("/api/compras")
    public ResponseEntity<CompraResponse> crear(@Valid @RequestBody CompraRequest req) {
        CompraResponse compra = servicio.crear(req);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(compra.id()).toUri()).body(compra);
    }

    @GetMapping("/api/compras/{id}")
    public CompraResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /** Resumen de compra en PDF con los datos reales (la ruta de S2 se mantiene por compatibilidad). */
    @GetMapping({"/api/compras/{id}/resumen", "/api/resumen/descargar/{id}"})
    public ResponseEntity<byte[]> resumen(@PathVariable Long id) {
        byte[] datos = servicio.resumenPdf(id);
        return Descargas.archivo(new ArchivoDescarga("resumen_compra_" + id + ".pdf", "application/pdf", datos,
                Checksums.sha256Hex(datos)));
    }
}

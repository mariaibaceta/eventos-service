package com.duoc.eventos_service.controller;

import com.duoc.eventos_service.dto.HistorialResponse;
import com.duoc.eventos_service.dto.TicketRequest;
import com.duoc.eventos_service.dto.TicketResponse;
import com.duoc.eventos_service.dto.TicketUpdateRequest;
import com.duoc.eventos_service.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService servicio;

    public TicketController(TicketService servicio) {
        this.servicio = servicio;
    }

    /** Genera el ticket PDF en EFS y lo sube automáticamente a S3. */
    @PostMapping
    public ResponseEntity<TicketResponse> generar(@Valid @RequestBody TicketRequest req) {
        TicketResponse ticket = servicio.generar(req);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(ticket.id()).toUri()).body(ticket);
    }

    /** Sube (o vuelve a subir) a S3 el ticket que está en EFS. */
    @PostMapping("/{id}/subir")
    public TicketResponse subir(@PathVariable Long id) {
        return servicio.subir(id);
    }

    @GetMapping("/{id}")
    public TicketResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /** Descarga un ticket desde S3; la cabecera X-Checksum-SHA256 permite verificar su integridad. */
    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) {
        return Descargas.archivo(servicio.descargar(id));
    }

    /** Descarga en un ZIP los tickets de un usuario, de un evento o de ambos. */
    @GetMapping("/descargar")
    public ResponseEntity<byte[]> descargarVarios(@RequestParam(required = false) String usuario,
                                                  @RequestParam(required = false) Long eventoId) {
        return Descargas.archivo(servicio.descargarZip(usuario, eventoId));
    }

    /** Modifica los detalles del ticket y reemplaza el PDF en la misma ruta de EFS y la misma clave de S3. */
    @PutMapping("/{id}")
    public TicketResponse modificar(@PathVariable Long id, @Valid @RequestBody TicketUpdateRequest req) {
        return servicio.modificar(id, req);
    }

    /** Elimina un ticket específico validando antes que exista. */
    @DeleteMapping("/{id}")
    public TicketResponse eliminar(@PathVariable Long id) {
        return servicio.eliminar(id);
    }

    /** Historial de tickets generados, con filtros opcionales y combinables. Fechas en formato yyyy-MM-dd. */
    @GetMapping("/historial")
    public HistorialResponse historial(@RequestParam(required = false) String usuario,
                                       @RequestParam(required = false) Long eventoId,
                                       @RequestParam(required = false) String estado,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                       LocalDate desde,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                       LocalDate hasta) {
        return servicio.historial(usuario, eventoId, estado, desde, hasta);
    }
}

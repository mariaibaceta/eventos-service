package com.duoc.eventos_service.controller;

import com.duoc.eventos_service.dto.EstadisticasEventoResponse;
import com.duoc.eventos_service.dto.EventoRequest;
import com.duoc.eventos_service.dto.EventoResponse;
import com.duoc.eventos_service.service.EventoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/eventos")
public class EventoController {

    private final EventoService servicio;

    public EventoController(EventoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<EventoResponse> listar() {
        return servicio.listar();
    }

    @GetMapping("/{id}")
    public EventoResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    public ResponseEntity<EventoResponse> crear(@Valid @RequestBody EventoRequest req) {
        EventoResponse creado = servicio.crear(req);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.id()).toUri()).body(creado);
    }

    /** Estadísticas de ventas y reservas de un evento. */
    @GetMapping("/{id}/estadisticas")
    public EstadisticasEventoResponse estadisticas(@PathVariable Long id) {
        return servicio.estadisticas(id);
    }

    /** Estadísticas de todos los eventos. */
    @GetMapping("/estadisticas")
    public List<EstadisticasEventoResponse> estadisticasTodos() {
        return servicio.estadisticasTodos();
    }
}

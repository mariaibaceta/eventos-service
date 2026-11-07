package com.duoc.eventos_service.service;

import com.duoc.eventos_service.dto.*;
import com.duoc.eventos_service.exception.RecursoNoEncontradoException;
import com.duoc.eventos_service.model.Evento;
import com.duoc.eventos_service.repository.CompraRepository;
import com.duoc.eventos_service.repository.EventoRepository;
import com.duoc.eventos_service.repository.TicketRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EventoService {

    private final EventoRepository eventos;
    private final CompraRepository compras;
    private final TicketRepository tickets;

    public EventoService(EventoRepository eventos, CompraRepository compras, TicketRepository tickets) {
        this.eventos = eventos;
        this.compras = compras;
        this.tickets = tickets;
    }

    public List<EventoResponse> listar() {
        return eventos.findAll(Sort.by("fecha", "id")).stream().map(this::aRespuesta).toList();
    }

    public EventoResponse obtener(Long id) {
        return aRespuesta(buscar(id));
    }

    @Transactional
    public EventoResponse crear(EventoRequest req) {
        Evento evento = new Evento();
        evento.setNombre(req.nombre().trim());
        evento.setFecha(req.fecha());
        evento.setLugar(req.lugar() == null || req.lugar().isBlank() ? null : req.lugar().trim());
        evento.setPrecio(req.precio());
        evento.setCapacidad(req.capacidad());
        return aRespuesta(eventos.save(evento));
    }

    public EstadisticasEventoResponse estadisticas(Long id) {
        return calcular(buscar(id));
    }

    public List<EstadisticasEventoResponse> estadisticasTodos() {
        return eventos.findAll(Sort.by("fecha", "id")).stream().map(this::calcular).toList();
    }

    Evento buscar(Long id) {
        return eventos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el evento con id " + id));
    }

    private EstadisticasEventoResponse calcular(Evento evento) {
        TotalesReservas reservas = compras.totalesPorEvento(evento.getId());
        TotalesVentas ventas = tickets.ventasPorEvento(evento.getId());
        long disponibles = Math.max(0, evento.getCapacidad() - reservas.entradas());
        BigDecimal ocupacion = BigDecimal.valueOf(reservas.entradas() * 100)
                .divide(BigDecimal.valueOf(evento.getCapacidad()), 2, RoundingMode.HALF_UP);
        return new EstadisticasEventoResponse(evento.getId(), evento.getNombre(), evento.getFecha(),
                evento.getCapacidad(), reservas.reservas(), reservas.entradas(), ventas.ventas(), ventas.entradas(),
                reservas.entradas() - ventas.entradas(), disponibles, ocupacion, ventas.recaudacion(),
                reservas.usuarios(), tickets.eliminadosPorEvento(evento.getId()));
    }

    private EventoResponse aRespuesta(Evento e) {
        long disponibles = Math.max(0, e.getCapacidad() - compras.entradasReservadas(e.getId()));
        return new EventoResponse(e.getId(), e.getNombre(), e.getFecha(), e.getLugar(), e.getPrecio(),
                e.getCapacidad(), disponibles);
    }
}

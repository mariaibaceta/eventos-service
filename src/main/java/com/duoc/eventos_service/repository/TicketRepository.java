package com.duoc.eventos_service.repository;

import com.duoc.eventos_service.dto.TotalesVentas;
import com.duoc.eventos_service.model.EstadoTicket;
import com.duoc.eventos_service.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("select t from Ticket t join fetch t.compra c join fetch c.evento where t.id = :id")
    Optional<Ticket> buscarCompleto(@Param("id") Long id);

    boolean existsByCompraIdAndEstadoNot(Long compraId, EstadoTicket estado);

    /** Historial con filtros opcionales; el filtrado y el orden se resuelven en la base de datos. */
    @Query("""
            select t from Ticket t join fetch t.compra c join fetch c.evento e
            where (:usuario is null or c.usuario = :usuario)
              and (:eventoId is null or e.id = :eventoId)
              and (:estado is null or t.estado = :estado)
              and (:desde is null or t.fechaCreacion >= :desde)
              and (:hasta is null or t.fechaCreacion < :hasta)
            order by t.fechaCreacion desc, t.id desc""")
    List<Ticket> historial(@Param("usuario") String usuario, @Param("eventoId") Long eventoId,
                           @Param("estado") EstadoTicket estado, @Param("desde") LocalDateTime desde,
                           @Param("hasta") LocalDateTime hasta);

    @Query("""
            select new com.duoc.eventos_service.dto.TotalesVentas(
                count(t), sum(c.cantidad), sum(c.precioTotal))
            from Ticket t join t.compra c
            where c.evento.id = :eventoId and t.estado <> com.duoc.eventos_service.model.EstadoTicket.ELIMINADO""")
    TotalesVentas ventasPorEvento(@Param("eventoId") Long eventoId);

    @Query("""
            select count(t) from Ticket t
            where t.compra.evento.id = :eventoId and t.estado = com.duoc.eventos_service.model.EstadoTicket.ELIMINADO""")
    long eliminadosPorEvento(@Param("eventoId") Long eventoId);
}

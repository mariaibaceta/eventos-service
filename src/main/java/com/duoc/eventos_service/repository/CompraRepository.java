package com.duoc.eventos_service.repository;

import com.duoc.eventos_service.dto.TotalesReservas;
import com.duoc.eventos_service.model.Compra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    @Query("select c from Compra c join fetch c.evento where c.id = :id")
    Optional<Compra> buscarConEvento(@Param("id") Long id);

    @Query("""
            select new com.duoc.eventos_service.dto.TotalesReservas(
                count(c), sum(c.cantidad), count(distinct c.usuario))
            from Compra c where c.evento.id = :eventoId""")
    TotalesReservas totalesPorEvento(@Param("eventoId") Long eventoId);

    @Query("select coalesce(sum(c.cantidad), 0) from Compra c where c.evento.id = :eventoId")
    long entradasReservadas(@Param("eventoId") Long eventoId);
}

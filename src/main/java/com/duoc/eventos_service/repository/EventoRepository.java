package com.duoc.eventos_service.repository;

import com.duoc.eventos_service.model.Evento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    /** Bloquea el evento mientras se valida la capacidad, para no vender más entradas que las disponibles. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Evento e where e.id = :id")
    Optional<Evento> bloquear(@Param("id") Long id);
}

package com.derso.arquitetura.reservasexterno.app;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.derso.arquitetura.reservasexterno.entity.Reserva;

public interface ReservasRepository extends JpaRepository<Reserva, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Reserva r
        SET
            r.confirmado = true
        WHERE r.id = :idReserva
            AND r.confirmado = false
            AND r.criacao >= :horaRef
    """)
    int confirmar(@Param("idReserva") UUID id, @Param("horaRef") Instant horaRef);

    @Modifying
    @Query("""
        DELETE FROM Reserva r
        WHERE r.id = :idReserva
            AND r.confirmado = false
    """)
    int remover(@Param("idReserva") UUID id);
    
}

package com.derso.arquitetura.reservasinterno;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.derso.arquitetura.reservasinterno.entity.Reserva;

public interface ReservasRepository extends JpaRepository<Reserva, UUID> {

    @Modifying
    @Transactional
    @Query("""
        UPDATE Reserva r
        SET r.status = 'RESERVADA'
        WHERE r.id = :id
            AND r.status = 'PRE_RESERVADA'
    """)
    int marcarReservada(@Param("id") UUID id);

    @Modifying
    @Transactional
    @Query("""
        UPDATE Reserva r
        SET r.status = 'PRE_RESERVADA'
        WHERE r.id = :id
            AND r.status = 'RESERVADA'
    """)
    int marcarPreReservada(@Param("id") UUID id);

}

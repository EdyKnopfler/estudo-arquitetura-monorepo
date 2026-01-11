package com.derso.arquitetura.reservasinterno;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.derso.arquitetura.reservasinterno.entity.Reserva;

public interface ReservasRepository extends JpaRepository<Reserva, UUID> {
    
}

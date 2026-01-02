package com.derso.arquitetura.reservasexterno.app;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.derso.arquitetura.reservasexterno.entity.Reserva;

public interface ReservasRepository extends JpaRepository<Reserva, UUID> {
    
}

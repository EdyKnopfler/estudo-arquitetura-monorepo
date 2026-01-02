package com.derso.arquitetura.reservasexterno.app;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.derso.arquitetura.reservasexterno.entity.Reserva;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservasService {

    private static final double CHANCE_FALHA = 0.25;

    private final ReservasRepository repositorio;

    // INTRODUZIMOS ALGUMA "ENTROPIA" PARA OS SERVIÇOS INTERNOS TRATAREM
    private void seraQueVaiFalhar() {
        if (Math.random() < CHANCE_FALHA) {
            throw new FalhaAleatoriaException();
        }
    }
    
    @Transactional
    public UUID criar(UUID idCliente) {
        seraQueVaiFalhar();
        Reserva reserva = new Reserva(idCliente);
        repositorio.save(reserva);
        return reserva.getId();
    }
}

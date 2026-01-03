package com.derso.arquitetura.reservasexterno.app;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.derso.arquitetura.reservasexterno.entity.Reserva;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservasService {

    private static final double CHANCE_FALHA = 0.25;
    private static final Duration TEMPO_MAXIMO = Duration.ofMinutes(15);

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

    @Transactional
    public void confirmar(UUID id) {
        seraQueVaiFalhar();
        Instant horaRef = Instant.now().minus(TEMPO_MAXIMO);

        if (repositorio.confirmar(id, horaRef) == 0) {
            throw new EntityNotFoundException("Reserva não encontrada ou expirada");
        }
    }

    @Transactional
    public void remover(UUID id) {
        seraQueVaiFalhar();

        if (repositorio.remover(id) == 0) {
            throw new EntityNotFoundException("Reserva não encontrada ou já confirmada");
        }
    }
}

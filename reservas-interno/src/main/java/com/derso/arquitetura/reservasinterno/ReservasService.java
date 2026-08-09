package com.derso.arquitetura.reservasinterno;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.derso.arquitetura.reservasinterno.dto.ReservaDTO;
import com.derso.arquitetura.reservasinterno.entity.Reserva;

import jakarta.persistence.EntityNotFoundException;

@Service
public class ReservasService {

    private static final Logger log = LoggerFactory.getLogger(ReservasService.class);

    private final TransactionTemplate transactionTemplate;
    private final ReservasRepository repositorio;
    private final ReservasExternoService servicoExterno;

    public ReservasService(
        PlatformTransactionManager transactionManager,
        ReservasRepository repositorio,
        ReservasExternoService servicoExterno
    ) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.repositorio = repositorio;
        this.servicoExterno = servicoExterno;
    }

    public ReservaDTO criarReserva(UUID idCliente) {

        // NUNCA chamamos o serviço externo dentro de uma transação

        UUID idExterno = servicoExterno.criar(idCliente);

        return transactionTemplate.execute(status -> {
            Reserva novaReserva = new Reserva(null, idExterno);
            repositorio.save(novaReserva);
            return new ReservaDTO(novaReserva.getId(), novaReserva.getIdExterno());
        });
    }

    // Adquire antes de liberar; liberação é melhor esforço. Ver docs/purchase-flow-design.md.
    public ReservaDTO trocarReserva(UUID idReservaAntiga, UUID idCliente) {
        Reserva reservaAntiga = repositorio.findById(idReservaAntiga)
            .orElseThrow(() -> new EntityNotFoundException("Reserva não encontrada: " + idReservaAntiga));

        UUID idExternoNovo = servicoExterno.criar(idCliente);

        Reserva novaReserva = transactionTemplate.execute(status -> {
            Reserva reserva = new Reserva(null, idExternoNovo);
            repositorio.save(reserva);
            return reserva;
        });

        liberarMelhorEsforco(reservaAntiga);

        return new ReservaDTO(novaReserva.getId(), novaReserva.getIdExterno());
    }

    private void liberarMelhorEsforco(Reserva reservaAntiga) {
        try {
            servicoExterno.cancelar(reservaAntiga.getIdExterno());
            repositorio.delete(reservaAntiga);
        } catch (Exception e) {
            log.warn("Falha ao liberar pré-reserva antiga {} (idExterno {}) — segue órfã até o timeout do serviço externo",
                reservaAntiga.getId(), reservaAntiga.getIdExterno(), e);
        }
    }

}

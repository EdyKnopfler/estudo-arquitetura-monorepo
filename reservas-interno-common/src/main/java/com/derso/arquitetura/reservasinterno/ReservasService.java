package com.derso.arquitetura.reservasinterno;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.derso.arquitetura.reservasinterno.dto.ReservaDTO;
import com.derso.arquitetura.reservasinterno.entity.Reserva;

@Service
public class ReservasService {

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
            Reserva novaReserva = new Reserva(idExterno);
            repositorio.save(novaReserva);
            return new ReservaDTO(novaReserva.getId(), novaReserva.getIdExterno());
        });
    }
    
}

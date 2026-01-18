package com.derso.arquitetura.reservasinternosagas.reservas;

import java.io.IOException;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.derso.arquitetura.sagas.SagasMessaging;

@Component
public class ReservasSagas implements SmartLifecycle {

    @Value("${sagas.estafila}")
    public String estaFila;

    @Value("${sagas.filaanterior:#{null}}")
    public String filaAnterior;

    @Value("${sagas.proximafila:#{null}}")
    public String proximaFila;

    private final SagasMessaging sagas;

    private boolean running = false;
    
    public ReservasSagas(SagasMessaging sagas) throws IOException {
        this.sagas = sagas;
    }
    
    @Override
    public void start() {
        try {
            Optional<String> optFilaAnterior = Optional.ofNullable(filaAnterior);
            Optional<String> optProximaFila = Optional.ofNullable(proximaFila);
            
            sagas.configurarServico(estaFila, optFilaAnterior, optProximaFila);
            
            sagas.iniciarConsumo(
                estaFila + "-consumer", 
                estaFila, 
                optFilaAnterior, 
                optProximaFila, 
                mensagem -> {

                    // TODO fazer o tratamento no nível do negócio
                    System.out.println("Recebida mensagem: " + mensagem);

                }
            );

            running = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        sagas.pararConsumo();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

}

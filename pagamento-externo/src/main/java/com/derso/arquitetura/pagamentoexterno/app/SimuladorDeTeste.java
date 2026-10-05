package com.derso.arquitetura.pagamentoexterno.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// simulacao.resultado fixa o desfecho do criar, simulacao.resultado-pagar o do pagar; ausente, sorteia
@Component
@Profile("test")
public class SimuladorDeTeste implements Simulador {

    private final ResultadoSimulado configurado;
    private final ResultadoPagar configuradoPagar;

    public SimuladorDeTeste(
        @Value("${simulacao.resultado:#{null}}") ResultadoSimulado configurado,
        @Value("${simulacao.resultado-pagar:#{null}}") ResultadoPagar configuradoPagar
    ) {
        this.configurado = configurado;
        this.configuradoPagar = configuradoPagar;
    }

    @Override
    public ResultadoSimulado decidir() {
        return configurado != null ? configurado : Simulador.sortear();
    }

    @Override
    public ResultadoPagar decidirPagar() {
        return configuradoPagar != null ? configuradoPagar : Simulador.sortearPagar();
    }

}

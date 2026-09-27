package com.derso.arquitetura.pagamentoexterno.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// simulacao.resultado fixa o desfecho do processo inteiro; ausente, sorteia
@Component
@Profile("test")
public class SimuladorDeTeste implements Simulador {

    private final ResultadoSimulado configurado;

    public SimuladorDeTeste(@Value("${simulacao.resultado:#{null}}") ResultadoSimulado configurado) {
        this.configurado = configurado;
    }

    @Override
    public ResultadoSimulado decidir() {
        return configurado != null ? configurado : Simulador.sortear();
    }

}

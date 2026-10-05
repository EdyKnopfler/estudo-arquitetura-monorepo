package com.derso.arquitetura.pagamentoexterno.app;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class SimuladorAleatorio implements Simulador {

    @Override
    public ResultadoSimulado decidir() {
        return Simulador.sortear();
    }

    @Override
    public ResultadoPagar decidirPagar() {
        return Simulador.sortearPagar();
    }

}

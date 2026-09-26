package com.derso.arquitetura.reservasexterno.app;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class SimuladorAleatorio implements Simulador {

    @Override
    public ResultadoSimulado decidir(String headerSimularResultado) {
        return Simulador.sortear();
    }

}

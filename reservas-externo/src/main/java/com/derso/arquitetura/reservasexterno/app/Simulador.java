package com.derso.arquitetura.reservasexterno.app;

// Implementação escolhida por profile: só `test` aceita desfecho forçado — docs/testing-strategy.md
public interface Simulador {

    double CHANCE_FALHA = 0.25;

    ResultadoSimulado decidir();

    static ResultadoSimulado sortear() {
        return Math.random() < CHANCE_FALHA ? ResultadoSimulado.FALHA_INFRA : ResultadoSimulado.SUCESSO;
    }

}

package com.derso.arquitetura.pagamentoexterno.app;

import com.derso.arquitetura.pagamentoexterno.PagamentoExternoApplication;

// Implementação escolhida por profile: só `test` aceita desfecho forçado — docs/testing-strategy.md
public interface Simulador {

    ResultadoSimulado decidir(String headerSimularResultado);

    static ResultadoSimulado sortear() {
        return Math.random() < PagamentoExternoApplication.CHANCE_FALHA
            ? ResultadoSimulado.FALHA_INFRA
            : ResultadoSimulado.SUCESSO;
    }

}

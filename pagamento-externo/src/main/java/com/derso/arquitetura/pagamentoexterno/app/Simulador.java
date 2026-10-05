package com.derso.arquitetura.pagamentoexterno.app;

import com.derso.arquitetura.pagamentoexterno.PagamentoExternoApplication;

// Implementação escolhida por profile: só `test` aceita desfecho forçado — docs/testing-strategy.md
// Ruim de propósito: CHANCE_FALHA dividida em dois momentos por operação
public interface Simulador {

    ResultadoSimulado decidir();

    ResultadoPagar decidirPagar();

    static ResultadoSimulado sortear() {
        double sorteio = Math.random();
        if (sorteio < PagamentoExternoApplication.CHANCE_FALHA / 2) {
            return ResultadoSimulado.FALHA_ANTES_DE_GRAVAR;
        }
        if (sorteio < PagamentoExternoApplication.CHANCE_FALHA) {
            return ResultadoSimulado.FALHA_DEPOIS_DE_GRAVAR;
        }
        return ResultadoSimulado.SUCESSO;
    }

    static ResultadoPagar sortearPagar() {
        double sorteio = Math.random();
        if (sorteio < PagamentoExternoApplication.CHANCE_FALHA / 2) {
            return ResultadoPagar.RECUSADO;
        }
        if (sorteio < PagamentoExternoApplication.CHANCE_FALHA) {
            return ResultadoPagar.PAGO_SEM_AVISO;
        }
        return ResultadoPagar.ACEITO;
    }

}

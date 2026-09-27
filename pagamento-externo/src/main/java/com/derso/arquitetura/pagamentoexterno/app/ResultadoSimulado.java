package com.derso.arquitetura.pagamentoexterno.app;

// Forçado via simulacao.resultado, só no profile `test` (ver SimuladorDeTeste). Fora de `test`,
// sempre sorteio (CHANCE_FALHA).
public enum ResultadoSimulado {
    SUCESSO,
    FALHA_NEGOCIO,
    FALHA_INFRA
}

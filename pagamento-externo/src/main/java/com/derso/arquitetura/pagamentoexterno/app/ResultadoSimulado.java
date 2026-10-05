package com.derso.arquitetura.pagamentoexterno.app;

// Forçado via simulacao.resultado, só no profile `test` (ver SimuladorDeTeste). Fora de `test`,
// sempre sorteio (CHANCE_FALHA).
public enum ResultadoSimulado {
    SUCESSO,
    FALHA_ANTES_DE_GRAVAR,
    // transação criada, resposta perdida: a falha ambígua que o chamador resolve repetindo a chave
    FALHA_DEPOIS_DE_GRAVAR
}

package com.derso.arquitetura.pagamentoexterno.app;

// Forçado via header X-Simular-Resultado ou simulacao.resultado, só no profile `test` (ver
// SimuladorDeTeste) — mesmo padrão de "negative testing" do sandbox da PayPal
// (header PayPal-Mock-Response). Fora de `test`, sempre sorteio (CHANCE_FALHA).
public enum ResultadoSimulado {
    SUCESSO,
    FALHA_NEGOCIO,
    FALHA_INFRA
}

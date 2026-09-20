package com.derso.arquitetura.pagamentoexterno.app;

// Cabeçalho X-Simular-Resultado (PagamentoController.HEADER_SIMULAR_RESULTADO) força um destes —
// mesmo padrão de "negative testing" do sandbox da PayPal (header PayPal-Mock-Response) e dos
// cartões de teste da Stripe: o resultado desejado viaja no request, não em config do processo,
// então funciona igual contra Testcontainers ou serviço já no ar (docs/testing-strategy.md).
// Ausente = cai no chaos aleatório de sempre (CHANCE_FALHA) — "aleatório" é só pra app rodando de
// verdade, testes pedem um dos 3 explicitamente.
public enum ResultadoSimulado {
    SUCESSO,
    FALHA_NEGOCIO,
    FALHA_INFRA
}

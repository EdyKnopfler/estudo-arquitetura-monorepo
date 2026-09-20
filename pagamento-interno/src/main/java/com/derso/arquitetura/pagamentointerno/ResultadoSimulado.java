package com.derso.arquitetura.pagamentointerno;

// Espelha o enum de mesmo nome em pagamento-externo (módulo separado, sem tipos compartilhados) —
// usado só pra montar o header X-Simular-Resultado em teste de integração; produção nunca passa isso
// (ver PagamentoExternoService.efetuar).
public enum ResultadoSimulado {
    SUCESSO,
    FALHA_NEGOCIO,
    FALHA_INFRA
}

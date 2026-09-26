package com.derso.arquitetura.reservasexterno.app;

// Forçado via header X-Simular-Resultado ou simulacao.resultado, só no profile `test` (ver
// SimuladorDeTeste). Sem FALHA_NEGOCIO: aqui falha de negócio é real (404 de reserva
// expirada/confirmada), não sorteada.
public enum ResultadoSimulado {
    SUCESSO,
    FALHA_INFRA
}

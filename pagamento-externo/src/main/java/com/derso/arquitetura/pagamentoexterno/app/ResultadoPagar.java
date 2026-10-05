package com.derso.arquitetura.pagamentoexterno.app;

// Forçado via simulacao.resultado-pagar, só no profile `test` (ver SimuladorDeTeste).
public enum ResultadoPagar {
    ACEITO,
    RECUSADO,
    // marca pago e cai antes do webhook: o interno só descobre pelo consultar
    PAGO_SEM_AVISO
}

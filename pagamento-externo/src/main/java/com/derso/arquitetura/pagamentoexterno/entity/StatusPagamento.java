package com.derso.arquitetura.pagamentoexterno.entity;

public enum StatusPagamento {
    PENDENTE,
    PAGO,
    CANCELADO,
    // nunca gravado: PENDENTE fora do prazo (Pagamento.statusEm)
    EXPIRADO
}

package com.derso.arquitetura.pagamentoexterno.entity;

public enum StatusPagamento {
    PENDENTE,
    PAGO,
    CANCELADO,
    // paga e devolvida (reversão da SAGA)
    ESTORNADO,
    // nunca gravado: PENDENTE fora do prazo (Pagamento.statusEm)
    EXPIRADO
}

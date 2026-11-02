package com.derso.arquitetura.pagamentointerno.entity;

public enum StatusPagamento {
    CRIANDO,
    AGUARDANDO_PAGAMENTO,
    // gravado só pelo webhook; achado ao renovar, só vai na resposta
    PAGO
}

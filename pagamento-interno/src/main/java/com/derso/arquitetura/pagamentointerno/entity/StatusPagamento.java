package com.derso.arquitetura.pagamentointerno.entity;

public enum StatusPagamento {
    CRIANDO,
    AGUARDANDO_PAGAMENTO,
    // ainda só na resposta: achado ao renovar, não é gravado — quem fecha o estado é o webhook
    PAGO
}

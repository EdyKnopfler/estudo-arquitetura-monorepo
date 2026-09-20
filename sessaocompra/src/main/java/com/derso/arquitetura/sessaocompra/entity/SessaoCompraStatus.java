package com.derso.arquitetura.sessaocompra.entity;

public enum SessaoCompraStatus {

    INICIADA,
    CRIANDO_PAGAMENTO,
    EFETUANDO_PAGAMENTO,
    PAGAMENTO_EFETUADO,
    VIAGEM_RESERVADA,
    ERRO,
    CANCELANDO,
    CANCELADA,
    FALHA_CANCELAMENTO;

    public boolean finalizada() {
        return this == VIAGEM_RESERVADA || this == CANCELADA || this == ERRO;
    }
}

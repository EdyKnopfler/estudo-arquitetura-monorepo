package com.derso.arquitetura.sessaocompra.entity;

public enum SessaoCompraStatus {

    INICIADA,
    EFETUANDO_PAGAMENTO,
    PAGAMENTO_EFETUADO,
    VIAGEM_RESERVADA,
    ERRO,
    CANCELADA;

    public boolean finalizada() {
        return this == VIAGEM_RESERVADA || this == CANCELADA || this == ERRO;
    }
}


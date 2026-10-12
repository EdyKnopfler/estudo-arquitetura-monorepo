package com.derso.arquitetura.sessaocompra.app.dto;

// O que vem agora pro front — docs/state-driven-endpoints.md. Cresce um caso por vez.
public enum EtapaCompra {
    PAGAR,
    JA_PAGO,
    // pagamento-interno ainda está criando o link: repetir depois
    AGUARDAR
}

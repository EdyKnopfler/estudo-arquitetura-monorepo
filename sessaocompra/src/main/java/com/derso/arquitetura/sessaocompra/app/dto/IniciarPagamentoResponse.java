package com.derso.arquitetura.sessaocompra.app.dto;

import java.time.Instant;

public record IniciarPagamentoResponse(
    EtapaCompra etapa,
    String urlPagamento,
    Instant vencimento
) {

}

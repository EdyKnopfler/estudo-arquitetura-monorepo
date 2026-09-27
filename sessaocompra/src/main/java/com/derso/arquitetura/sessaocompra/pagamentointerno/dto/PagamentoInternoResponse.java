package com.derso.arquitetura.sessaocompra.pagamentointerno.dto;

import java.util.UUID;

// Espelha PagamentoDTO de pagamento-interno (módulo separado, sem tipos compartilhados).
public record PagamentoInternoResponse(
    UUID id,
    String urlPagamento
) {

}

package com.derso.arquitetura.sessaocompra.pagamentointerno.dto;

import java.time.Instant;
import java.util.UUID;

// Espelha PagamentoDTO de pagamento-interno (módulo separado, sem tipos compartilhados).
public record PagamentoInternoResponse(
    UUID id,
    String status,
    String urlPagamento,
    Instant vencimento
) {

}

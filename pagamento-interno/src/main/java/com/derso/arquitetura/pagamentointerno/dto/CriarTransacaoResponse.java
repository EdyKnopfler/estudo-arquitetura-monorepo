package com.derso.arquitetura.pagamentointerno.dto;

import java.util.UUID;

// Espelha PagamentoResponseDTO de pagamento-externo (módulo separado, sem tipos compartilhados).
public record CriarTransacaoResponse(
    UUID idTransacao,
    String status,
    String urlPagamento
) {

}

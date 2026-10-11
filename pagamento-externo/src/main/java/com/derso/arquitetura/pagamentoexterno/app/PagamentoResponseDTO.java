package com.derso.arquitetura.pagamentoexterno.app;

import java.time.Instant;
import java.util.UUID;

public record PagamentoResponseDTO(
    UUID idTransacao,
    String status,
    String urlPagamento,
    Instant vencimento
) {

}

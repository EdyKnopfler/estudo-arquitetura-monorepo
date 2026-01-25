package com.derso.arquitetura.pagamentoexterno.app;

import java.util.UUID;

public record PagamentoResponseDTO(
    UUID idTransacao,
    String status
) {
    
}

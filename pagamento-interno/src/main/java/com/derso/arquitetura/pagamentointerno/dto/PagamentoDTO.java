package com.derso.arquitetura.pagamentointerno.dto;

import java.util.UUID;

// Só o id interno — idExterno não cruza a fronteira de pagamento-interno. Ver
// docs/purchase-flow-design.md#payload-da-mensagem-da-saga.
public record PagamentoDTO(
    UUID id
) {

}

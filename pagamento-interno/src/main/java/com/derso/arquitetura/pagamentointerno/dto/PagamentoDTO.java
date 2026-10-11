package com.derso.arquitetura.pagamentointerno.dto;

import java.time.Instant;
import java.util.UUID;

import com.derso.arquitetura.pagamentointerno.entity.Pagamento;
import com.derso.arquitetura.pagamentointerno.entity.StatusPagamento;

// Responde a etapa do pagamento, não só a URL — docs/state-driven-endpoints.md.
// idExterno não cruza a fronteira de pagamento-interno. Ver
// docs/purchase-flow-design.md#payload-da-mensagem-da-saga.
public record PagamentoDTO(
    UUID id,
    StatusPagamento status,
    String urlPagamento,
    Instant vencimento
) {

    public static PagamentoDTO de(Pagamento pagamento) {
        return new PagamentoDTO(
            pagamento.getId(), pagamento.getStatus(), pagamento.getUrlPagamento(), pagamento.getVencimento()
        );
    }

}

package com.derso.arquitetura.pagamentointerno.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

// Espelha WebhookRequestDTO de pagamento-externo (módulo separado, sem tipos compartilhados).
public record WebhookRequest(
    @NotNull UUID idTransacao,
    @NotNull String status
) {

}

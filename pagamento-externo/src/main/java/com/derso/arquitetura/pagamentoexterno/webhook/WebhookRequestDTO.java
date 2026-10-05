package com.derso.arquitetura.pagamentoexterno.webhook;

import java.util.UUID;

public record WebhookRequestDTO(
    UUID idTransacao,
    String status
) {

}

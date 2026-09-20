package com.derso.arquitetura.pagamentoexterno.webhook;

// TODO falta idTransacao — sem ele pagamento-interno não consegue achar a linha certa em `pagamentos`
// quando o callback chegar (WHERE id_externo = idTransacao). Ver WebhookService.enviarResposta (já tem
// o valor, só não repassa) e docs/purchase-flow-design.md#payload-da-mensagem-da-saga.
public record WebhookRequestDTO(
    String status
) {

}

package com.derso.arquitetura.pagamentointerno.app;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.derso.arquitetura.sagas.Messaging;

@RestController
@Profile("web")
public class PagamentoInternoController {

    private static final String FILA_PAGAMENTO = "pagamento";

    private final Messaging sagas;

    public PagamentoInternoController(Messaging sagas) throws IOException {
        this.sagas = sagas;
        sagas.configurarServico(FILA_PAGAMENTO, Optional.empty(), Optional.empty());
    }

    // TODO corpo do webhook hoje é vazio — precisa receber idTransacao+status (WebhookRequestDTO em
    // pagamento-externo também precisa desse campo, ver TODO lá). Buscar `pagamentos WHERE id_externo =
    // idTransacao` e montar a mensagem completa (idPagamento, idSessaoCompra, idReservaHotel,
    // idReservaVooIda, idReservaVooVolta — só ids internos) em vez de só tipo+rastreio.
    // Ver docs/purchase-flow-design.md#payload-da-mensagem-da-saga e docs/todo.md
    // ("validação de assinatura/origem e proteção contra reprocessamento" — pendência separada, mesma seção).
    @PostMapping("/webhook")
    public void webhookServicoExterno() throws IOException {
        String rastreio = UUID.randomUUID().toString();

        sagas.publicar(FILA_PAGAMENTO, Map.of(
                "tipo", Messaging.EXECUTE,
                "rastreio", rastreio
        ));

        System.out.println("[webhook] disparando SAGA rastreio=" + rastreio);
    }
}

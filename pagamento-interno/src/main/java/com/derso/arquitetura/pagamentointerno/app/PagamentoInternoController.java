package com.derso.arquitetura.pagamentointerno.app;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.derso.arquitetura.pagamentointerno.PagamentoService;
import com.derso.arquitetura.pagamentointerno.dto.CriarPagamentoRequest;
import com.derso.arquitetura.pagamentointerno.dto.PagamentoDTO;
import com.derso.arquitetura.sagas.Messaging;

import jakarta.validation.Valid;

@RestController
@Profile("web")
public class PagamentoInternoController {

    private static final String FILA_PAGAMENTO = "pagamento";

    private final Messaging sagas;
    private final PagamentoService servico;

    public PagamentoInternoController(Messaging sagas, PagamentoService servico) throws IOException {
        this.sagas = sagas;
        this.servico = servico;
        sagas.configurarServico(FILA_PAGAMENTO, Optional.empty(), Optional.empty());
    }

    // Chamada por sessaocompra.iniciarPagamento — ver docs/purchase-flow-design.md#payload-da-mensagem-da-saga.
    @PostMapping("/pagamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public PagamentoDTO criar(@RequestBody @Valid CriarPagamentoRequest dados) {
        return servico.criarPagamento(
            dados.idSessaoCompra(), dados.idReservaHotel(), dados.idReservaVooIda(), dados.idReservaVooVolta()
        );
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

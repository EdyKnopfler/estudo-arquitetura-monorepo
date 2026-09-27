package com.derso.arquitetura.pagamentointerno.app;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    // Chamada por sessaocompra.iniciarPagamento; PUT porque repetir devolve o mesmo pagamento —
    // ver docs/purchase-flow-design.md#criação-do-pagamento.
    @PutMapping("/pagamentos/{idSessaoCompra}")
    public PagamentoDTO criar(@PathVariable("idSessaoCompra") UUID idSessaoCompra, @RequestBody @Valid CriarPagamentoRequest dados) {
        return servico.criarPagamento(
            idSessaoCompra, dados.idReservaHotel(), dados.idReservaVooIda(), dados.idReservaVooVolta()
        );
    }

    // TODO corpo do webhook hoje é vazio — precisa receber idTransacao+status (WebhookRequestDTO em
    // pagamento-externo também precisa desse campo, ver TODO lá). Buscar `pagamentos WHERE id_externo =
    // idTransacao` e montar a mensagem completa (idPagamento, idSessaoCompra, idReservaHotel,
    // idReservaVooIda, idReservaVooVolta — só ids internos) em vez de só tipo+rastreio.
    // Ver docs/purchase-flow-design.md#payload-da-mensagem-da-saga e docs/todo.md.
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

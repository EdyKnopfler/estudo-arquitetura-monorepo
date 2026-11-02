package com.derso.arquitetura.pagamentointerno.app;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.derso.arquitetura.pagamentointerno.PagamentoService;
import com.derso.arquitetura.pagamentointerno.WebhookService;
import com.derso.arquitetura.pagamentointerno.dto.CriarPagamentoRequest;
import com.derso.arquitetura.pagamentointerno.dto.PagamentoDTO;
import com.derso.arquitetura.pagamentointerno.dto.WebhookRequest;

import jakarta.validation.Valid;

@RestController
@Profile("web")
public class PagamentoInternoController {

    private final PagamentoService servico;
    private final WebhookService webhook;

    public PagamentoInternoController(PagamentoService servico, WebhookService webhook) {
        this.servico = servico;
        this.webhook = webhook;
    }

    // Chamada por sessaocompra.iniciarPagamento; PUT porque repetir devolve o mesmo pagamento —
    // ver docs/purchase-flow-design.md#criação-do-pagamento.
    @PutMapping("/pagamentos/{idSessaoCompra}")
    public PagamentoDTO criar(@PathVariable("idSessaoCompra") UUID idSessaoCompra, @RequestBody @Valid CriarPagamentoRequest dados) {
        return servico.criarPagamento(
            idSessaoCompra, dados.idReservaHotel(), dados.idReservaVooIda(), dados.idReservaVooVolta()
        );
    }

    // Chamado por pagamento-externo
    @PostMapping("/webhook")
    public void webhook(@RequestBody @Valid WebhookRequest aviso) {
        webhook.receber(aviso.idTransacao(), aviso.status());
    }
}

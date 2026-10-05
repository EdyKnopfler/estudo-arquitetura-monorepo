package com.derso.arquitetura.pagamentoexterno.app;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.derso.arquitetura.pagamentoexterno.PagamentoService;
import com.derso.arquitetura.pagamentoexterno.entity.Pagamento;

import jakarta.validation.Valid;

@RestController
public class PagamentoController {

    private final PagamentoService servico;
    private final String urlPagamentoBase;

    public PagamentoController(PagamentoService servico, @Value("${simulacao.url-pagamento-base}") String urlPagamentoBase) {
        this.servico = servico;
        this.urlPagamentoBase = urlPagamentoBase;
    }

    @PostMapping("/criar")
    public PagamentoResponseDTO criar(
        @Valid @RequestBody PagamentoRequestDTO dados,
        @RequestHeader("Idempotency-Key") UUID chave,
        Authentication authentication
    ) {
        String idCliente = authentication.getPrincipal().toString();
        return resposta(servico.criar(idCliente, chave, dados.metodo(), dados.valor()));
    }

    // Simula o usuário na tela do gateway: rota pública (SecurityConfiguration)
    @PostMapping("/pagar/{idTransacao}")
    public PagamentoResponseDTO pagar(@PathVariable("idTransacao") UUID idTransacao) {
        return resposta(servico.pagar(idTransacao));
    }

    @GetMapping("/consultar/{idTransacao}")
    public PagamentoResponseDTO consultar(@PathVariable("idTransacao") UUID idTransacao, Authentication authentication) {
        return resposta(servico.consultar(authentication.getPrincipal().toString(), idTransacao));
    }

    @PostMapping("/cancelar/{idTransacao}")
    public PagamentoResponseDTO cancelar(@PathVariable("idTransacao") UUID idTransacao, Authentication authentication) {
        return resposta(servico.cancelar(authentication.getPrincipal().toString(), idTransacao));
    }

    private PagamentoResponseDTO resposta(Pagamento pagamento) {
        return new PagamentoResponseDTO(
            pagamento.getId(),
            pagamento.statusEm(Instant.now()).name(),
            urlPagamentoBase + "/" + pagamento.getId()
        );
    }

}

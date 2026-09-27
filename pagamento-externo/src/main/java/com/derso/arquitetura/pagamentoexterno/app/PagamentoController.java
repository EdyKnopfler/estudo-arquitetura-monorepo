package com.derso.arquitetura.pagamentoexterno.app;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.derso.arquitetura.pagamentoexterno.webhook.WebhookService;
import com.derso.arquitetura.webbase.config.BusinessException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PagamentoController {

    private final WebhookService webhook;
    private final Simulador simulador;

    @PostMapping("/efetuar")
    public ResponseEntity<PagamentoResponseDTO> efetuarPagamento(
        @Valid @RequestBody PagamentoRequestDTO dados,
        Authentication authentication
    ) {
        ResultadoSimulado resultado = simulador.decidir();

        if (resultado == ResultadoSimulado.FALHA_INFRA) {
            throw new RuntimeException("Falhou por motivo de: " + UUID.randomUUID().toString());
        }
        if (resultado == ResultadoSimulado.FALHA_NEGOCIO) {
            throw new BusinessException("Pagamento recusado (simulado)");
        }

        String idCliente = authentication.getPrincipal().toString();
        UUID idTransacao = UUID.randomUUID();

        // TODO desacoplar: webhook chamado síncrono, ANTES do 202 — pagamento-interno recebe o callback
        // antes de gravar a linha em `pagamentos`, e falha no webhook vira 500 no /efetuar. Gateway real
        // responde primeiro e notifica depois (assíncrono).
        webhook.enviarResposta(idCliente, idTransacao);

        return ResponseEntity.accepted().body(new PagamentoResponseDTO(idTransacao, "processando"));
    }

}

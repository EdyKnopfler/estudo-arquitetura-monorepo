package com.derso.arquitetura.pagamentoexterno.app;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.derso.arquitetura.pagamentoexterno.PagamentoExternoApplication;
import com.derso.arquitetura.pagamentoexterno.webhook.WebhookService;
import com.derso.arquitetura.webbase.config.BusinessException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PagamentoController {

    public static final String HEADER_SIMULAR_RESULTADO = "X-Simular-Resultado";

    private final WebhookService webhook;

    @PostMapping("/efetuar")
    public ResponseEntity<PagamentoResponseDTO> efetuarPagamento(
        @Valid @RequestBody PagamentoRequestDTO dados,
        @RequestHeader(value = HEADER_SIMULAR_RESULTADO, required = false) ResultadoSimulado resultadoSimulado,
        Authentication authentication
    ) {
        ResultadoSimulado resultado = resultadoSimulado != null ? resultadoSimulado : sortear();

        if (resultado == ResultadoSimulado.FALHA_INFRA) {
            throw new RuntimeException("Falhou por motivo de: " + UUID.randomUUID().toString());
        }
        if (resultado == ResultadoSimulado.FALHA_NEGOCIO) {
            throw new BusinessException("Pagamento recusado (simulado)");
        }

        String idCliente = authentication.getPrincipal().toString();
        UUID idTransacao = UUID.randomUUID();

        // Já enviamos a resposta do pagamento para o webhook da aplicação
        webhook.enviarResposta(idCliente, idTransacao);

        return ResponseEntity.accepted().body(new PagamentoResponseDTO(idTransacao, "processando"));
    }

    // Mesma proporção/comportamento de sempre — sucesso ou o que hoje era a única falha existente
    // (RuntimeException não tratada, agora nomeada FALHA_INFRA). Ver ResultadoSimulado.
    private static ResultadoSimulado sortear() {
        return Math.random() < PagamentoExternoApplication.CHANCE_FALHA
            ? ResultadoSimulado.FALHA_INFRA
            : ResultadoSimulado.SUCESSO;
    }

}

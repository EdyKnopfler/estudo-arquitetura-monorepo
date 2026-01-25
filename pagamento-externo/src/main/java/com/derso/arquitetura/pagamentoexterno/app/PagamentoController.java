package com.derso.arquitetura.pagamentoexterno.app;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.derso.arquitetura.pagamentoexterno.PagamentoExternoApplication;
import com.derso.arquitetura.pagamentoexterno.webhook.WebhookService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PagamentoController {

    private final WebhookService webhook; 
    
    @PostMapping("/efetuar")
    public ResponseEntity<PagamentoResponseDTO> efetuarPagamento(
        @Valid @RequestBody PagamentoRequestDTO dados,
        Authentication authentication
    ) {
        if (Math.random() < PagamentoExternoApplication.CHANCE_FALHA) {
            throw new RuntimeException("Falhou por motivo de: " + UUID.randomUUID().toString());
        }

        String idCliente = authentication.getPrincipal().toString();
        UUID idTransacao = UUID.randomUUID();

        // Já enviamos a resposta do pagamento para o webhook da aplicação
        webhook.enviarResposta(idCliente, idTransacao);

        return ResponseEntity.accepted().body(new PagamentoResponseDTO(idTransacao, "processando"));
    }

}

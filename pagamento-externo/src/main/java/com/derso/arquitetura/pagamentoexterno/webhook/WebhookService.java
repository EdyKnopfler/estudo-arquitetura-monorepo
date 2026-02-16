package com.derso.arquitetura.pagamentoexterno.webhook;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.derso.arquitetura.pagamentoexterno.PagamentoExternoApplication;
import com.derso.arquitetura.pagamentoexterno.config.WebhookConfig;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookConfig config;

    public void enviarResposta(String idCliente, UUID idTransacao) {
        String clienteWebhookUrl = config.getUrlsById().get(idCliente);

        // Usando o mesmo secredo na resposta para o serviço interno
        RestClient restClient = RestClient.builder()
            .defaultHeader("X-Client-Id", idCliente)
            .defaultHeader("X-Client-Secret", config.getSecretsById().get(idCliente))
            .build();

        boolean recusado = Math.random() < PagamentoExternoApplication.CHANCE_FALHA;

        WebhookRequestDTO requestData = new WebhookRequestDTO(recusado ? "recusado" : "OK");

        restClient.post()
            .uri(clienteWebhookUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestData)
            .retrieve()
            .toBodilessEntity();
    }


    
}

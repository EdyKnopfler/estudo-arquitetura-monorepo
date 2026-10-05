package com.derso.arquitetura.pagamentoexterno.webhook;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.derso.arquitetura.pagamentoexterno.config.WebhookConfig;
import com.derso.arquitetura.webbase.http.TimeoutHttp;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookConfig config;

    public void avisar(String idCliente, UUID idTransacao, String status) {
        String clienteWebhookUrl = config.getUrlsById().get(idCliente);

        // Usando o mesmo secredo na resposta para o serviço interno
        RestClient restClient = RestClient.builder()
            .requestFactory(TimeoutHttp.padrao())
            .defaultHeader("X-Client-Id", idCliente)
            .defaultHeader("X-Client-Secret", config.getSecretsById().get(idCliente))
            .build();

        restClient.post()
            .uri(clienteWebhookUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new WebhookRequestDTO(idTransacao, status))
            .retrieve()
            .toBodilessEntity();
    }

}

package com.derso.arquitetura.pagamentointerno.sessaocompra;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.derso.arquitetura.webbase.http.TimeoutHttp;

@Component
public class SessaoCompraClient {

    private final RestClient restClient;

    public SessaoCompraClient(
        @Value("${sessaocompra.url}") String urlServico,
        @Value("${sessaocompra.client-id}") String clientId,
        @Value("${sessaocompra.client-secret}") String clientSecret
    ) {
        this.restClient = RestClient.builder()
            .baseUrl(urlServico)
            .requestFactory(TimeoutHttp.padrao())
            .defaultHeader("X-Client-Id", clientId)
            .defaultHeader("X-Client-Secret", clientSecret)
            .build();
    }

    public void pagamentoEfetuado(UUID idSessaoCompra) {
        restClient.put()
            .uri("/servico/sessoes/{id}/pagamento-efetuado", idSessaoCompra)
            .retrieve()
            .toBodilessEntity();
    }

    public void pagamentoRecusado(UUID idSessaoCompra) {
        restClient.put()
            .uri("/servico/sessoes/{id}/pagamento-recusado", idSessaoCompra)
            .retrieve()
            .toBodilessEntity();
    }

}

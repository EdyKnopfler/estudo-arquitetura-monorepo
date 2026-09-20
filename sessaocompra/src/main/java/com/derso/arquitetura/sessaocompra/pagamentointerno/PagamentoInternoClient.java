package com.derso.arquitetura.sessaocompra.pagamentointerno;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.derso.arquitetura.sessaocompra.pagamentointerno.dto.CriarPagamentoInternoRequest;

@Service
public class PagamentoInternoClient {

    private final RestClient restClient;

    public PagamentoInternoClient(
        @Value("${pagamento-interno.url}") String urlServico,
        @Value("${pagamento-interno.client-id}") String clientId,
        @Value("${pagamento-interno.client-secret}") String clientSecret
    ) {
        this.restClient = RestClient.builder()
            .baseUrl(urlServico)
            .defaultHeader("X-Client-Id", clientId)
            .defaultHeader("X-Client-Secret", clientSecret)
            .build();
    }

    public void criar(UUID idSessaoCompra, UUID idReservaHotel, UUID idReservaVooIda, UUID idReservaVooVolta) {
        restClient.post()
            .uri("/pagamentos")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CriarPagamentoInternoRequest(idSessaoCompra, idReservaHotel, idReservaVooIda, idReservaVooVolta))
            .retrieve()
            .toBodilessEntity();
    }

}

package com.derso.arquitetura.sessaocompra.reservasinterno;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.derso.arquitetura.sessaocompra.reservasinterno.dto.CriarReservaInternaRequest;
import com.derso.arquitetura.sessaocompra.reservasinterno.dto.ReservaInternaResponse;

@Service
public class ReservasInternoHotelClient {

    private final RestClient restClient;

    public ReservasInternoHotelClient(
        @Value("${reservas-interno-hotel.url}") String urlServico,
        @Value("${reservas-interno-hotel.client-id}") String clientId,
        @Value("${reservas-interno-hotel.client-secret}") String clientSecret
    ) {
        this.restClient = RestClient.builder()
            .baseUrl(urlServico)
            .defaultHeader("X-Client-Id", clientId)
            .defaultHeader("X-Client-Secret", clientSecret)
            .build();
    }

    public UUID criar(UUID idCliente) {
        ReservaInternaResponse resposta = restClient.post()
            .uri("/reservas")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CriarReservaInternaRequest(idCliente))
            .retrieve()
            .body(ReservaInternaResponse.class);

        return resposta.id();
    }

    public UUID trocar(UUID idReservaAntiga, UUID idCliente) {
        ReservaInternaResponse resposta = restClient.put()
            .uri("/reservas/{id}/trocar", idReservaAntiga)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CriarReservaInternaRequest(idCliente))
            .retrieve()
            .body(ReservaInternaResponse.class);

        return resposta.id();
    }

}

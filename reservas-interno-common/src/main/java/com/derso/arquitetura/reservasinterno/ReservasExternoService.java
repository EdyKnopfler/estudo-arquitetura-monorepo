package com.derso.arquitetura.reservasinterno;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.derso.arquitetura.reservasinterno.dto.ReservaExternoRequest;
import com.derso.arquitetura.reservasinterno.dto.ReservaExternoResponse;

@Service
public class ReservasExternoService {

  private final RestClient restClient;

  public ReservasExternoService(
    @Value("${external-backend.url}") String urlServico,
    @Value("${external-backend.client-id}") String clientId,
    @Value("${external-backend.client-secret}") String clientSecret
  ) {
    this.restClient = RestClient.builder()
      .baseUrl(urlServico)
      .defaultHeader("X-Client-Id", clientId)
      .defaultHeader("X-Client-Secret", clientSecret)
      .build();
  }

  public UUID criar(UUID idCliente) {
    ReservaExternoRequest requestData = new ReservaExternoRequest(idCliente);

    ReservaExternoResponse responseData = 
        novoRequest("", requestData, ReservaExternoResponse.class);

    return responseData.idReserva();
  }

  private <TReq, TRes> TRes novoRequest(String finalDaUrl, TReq requestData, Class<TRes> responseClass) {
    return restClient.post()
        .uri("/reservas" + finalDaUrl)
        .contentType(MediaType.APPLICATION_JSON)
        .body(requestData)
        .retrieve()
        .body(responseClass);
  }

}

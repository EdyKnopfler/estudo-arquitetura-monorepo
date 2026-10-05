package com.derso.arquitetura.pagamentointerno;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.derso.arquitetura.pagamentointerno.dto.CriarTransacaoRequest;
import com.derso.arquitetura.pagamentointerno.dto.CriarTransacaoResponse;
import com.derso.arquitetura.webbase.http.TimeoutHttp;

@Service
public class PagamentoExternoService {

    private static final String HEADER_IDEMPOTENCIA = "Idempotency-Key";

    private final RestClient restClient;

    public PagamentoExternoService(
        @Value("${external-backend.url}") String urlServico,
        @Value("${external-backend.client-id}") String clientId,
        @Value("${external-backend.client-secret}") String clientSecret
    ) {
        // timeout vira falha ambígua no PagamentoService, em vez de travar o PUT
        this.restClient = RestClient.builder()
            .baseUrl(urlServico)
            .requestFactory(TimeoutHttp.padrao())
            .defaultHeader("X-Client-Id", clientId)
            .defaultHeader("X-Client-Secret", clientSecret)
            .build();
    }

    public CriarTransacaoResponse criar(String metodo, BigDecimal valor, UUID chaveIdempotencia) {
        return restClient.post()
            .uri("/criar")
            .contentType(MediaType.APPLICATION_JSON)
            .header(HEADER_IDEMPOTENCIA, chaveIdempotencia.toString())
            .body(new CriarTransacaoRequest(metodo, valor))
            .retrieve()
            .body(CriarTransacaoResponse.class);
    }

}

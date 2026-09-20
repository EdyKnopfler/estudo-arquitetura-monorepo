package com.derso.arquitetura.pagamentointerno;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.derso.arquitetura.pagamentointerno.dto.EfetuarPagamentoRequest;
import com.derso.arquitetura.pagamentointerno.dto.EfetuarPagamentoResponse;

@Service
public class PagamentoExternoService {

    // Sem timeout aqui, um hang do lado de lá trava a chamada síncrona indefinidamente sem nunca
    // cair no catch de SessaoCompraService.iniciarPagamento — ver docs/todo.md. Valores generosos
    // pra um endpoint que só gera um id e devolve; os outros RestClient do projeto ainda não têm
    // isso (avaliar depois).
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    // Mesmo nome de header que PagamentoController.HEADER_SIMULAR_RESULTADO em pagamento-externo
    // (módulo separado, sem constante compartilhada). Ver ResultadoSimulado.
    private static final String HEADER_SIMULAR_RESULTADO = "X-Simular-Resultado";

    private final RestClient restClient;

    public PagamentoExternoService(
        @Value("${external-backend.url}") String urlServico,
        @Value("${external-backend.client-id}") String clientId,
        @Value("${external-backend.client-secret}") String clientSecret
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
            .baseUrl(urlServico)
            .requestFactory(requestFactory)
            .defaultHeader("X-Client-Id", clientId)
            .defaultHeader("X-Client-Secret", clientSecret)
            .build();
    }

    public UUID efetuar(String metodo, BigDecimal valor) {
        return efetuar(metodo, valor, null);
    }

    // package-private — só teste de integração passa resultadoSimulado, pra forçar um dos 3
    // desfechos deterministicamente contra o pagamento-externo real. Produção sempre usa o overload
    // acima (resultadoSimulado null = header ausente = aleatório de verdade do lado de lá).
    UUID efetuar(String metodo, BigDecimal valor, ResultadoSimulado resultadoSimulado) {
        EfetuarPagamentoResponse resposta = restClient.post()
            .uri("/efetuar")
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> {
                if (resultadoSimulado != null) {
                    headers.add(HEADER_SIMULAR_RESULTADO, resultadoSimulado.name());
                }
            })
            .body(new EfetuarPagamentoRequest(metodo, valor))
            .retrieve()
            .body(EfetuarPagamentoResponse.class);

        return resposta.idTransacao();
    }

}

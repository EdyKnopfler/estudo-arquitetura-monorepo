package com.derso.arquitetura.pagamentointerno;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

// Integração de verdade: PagamentoExternoService conversando por HTTP com um pagamento-externo
// real (sem mock) — exige `docker-compose up` no ar, mesmo padrão-default de
// docs/testing-strategy.md (reaproveita o serviço já rodando, sem overhead de Testcontainers).
// Usa X-Simular-Resultado (ver ResultadoSimulado/PagamentoController em pagamento-externo) pra
// forçar cada um dos 3 desfechos deterministicamente, sem depender do CHANCE_FALHA aleatório.
class PagamentoExternoServiceIntegrationTest {

    private static final String URL =
        "http://" + envOrDefault("PAGAMENTO_EXTERNO_HOST", "localhost") + ":" + envOrDefault("PAGAMENTO_EXTERNO_PORT", "8086");

    // Credenciais default de .env.example (internal-backend.clients de pagamento-externo).
    private final PagamentoExternoService servico =
        new PagamentoExternoService(URL, "pagamentoExternoId", "pagamentoExternoSecret");

    @Test
    void resultadoSucessoDevolveIdTransacao() {
        UUID idTransacao = servico.efetuar("cartao", new BigDecimal("100.00"), ResultadoSimulado.SUCESSO);

        assertNotNull(idTransacao);
    }

    @Test
    void resultadoFalhaDeNegocioDevolve409() {
        assertThrows(HttpClientErrorException.Conflict.class, () ->
            servico.efetuar("cartao", new BigDecimal("100.00"), ResultadoSimulado.FALHA_NEGOCIO)
        );
    }

    @Test
    void resultadoFalhaDeInfraDevolve500() {
        assertThrows(HttpServerErrorException.class, () ->
            servico.efetuar("cartao", new BigDecimal("100.00"), ResultadoSimulado.FALHA_INFRA)
        );
    }

    private static String envOrDefault(String variavel, String fallback) {
        String valor = System.getenv(variavel);
        return valor != null ? valor : fallback;
    }

}

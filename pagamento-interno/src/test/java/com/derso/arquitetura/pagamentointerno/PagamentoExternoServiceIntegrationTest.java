package com.derso.arquitetura.pagamentointerno;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.derso.arquitetura.webbase.teste.ServicoEmContainer;

// Contrato HTTP real com pagamento-externo em container (ServicoEmContainer), profile `test`
// pra aceitar X-Simular-Resultado. Integrado: só roda com -Pintegrado (docs/testing-strategy.md).
@Tag("integrado")
class PagamentoExternoServiceIntegrationTest {

    private static final int PORTA = 8086;

    private static GenericContainer<?> pagamentoExterno;
    private static PagamentoExternoService servico;

    @BeforeAll
    static void subirPagamentoExterno() {
        pagamentoExterno = ServicoEmContainer.de("pagamento-externo")
            .withExposedPorts(PORTA)
            .withEnv("SPRING_PROFILES_ACTIVE", "test")
            .waitingFor(Wait.forLogMessage(".*Started PagamentoExternoApplication.*\\n", 1)
                .withStartupTimeout(Duration.ofMinutes(3)));
        pagamentoExterno.start();

        // credenciais default de internal-backend.clients de pagamento-externo
        servico = new PagamentoExternoService(
            "http://" + pagamentoExterno.getHost() + ":" + pagamentoExterno.getMappedPort(PORTA),
            "pagamentoExternoId", "pagamentoExternoSecret");
    }

    @AfterAll
    static void pararPagamentoExterno() {
        if (pagamentoExterno != null) {
            pagamentoExterno.stop();
        }
    }

    @Test
    @Disabled("SUCESSO chama o webhook síncrono antes de responder — volta após o desacoplamento (TODO em PagamentoController de pagamento-externo)")
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

}

package com.derso.arquitetura.pagamentointerno;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.derso.arquitetura.pagamentointerno.dto.EfetuarPagamentoResponse;
import com.derso.arquitetura.webbase.teste.ServicoEmContainer;

// Contrato HTTP real com pagamento-externo em container (ServicoEmContainer). Desfecho do simulador é
// config do processo (SIMULACAO_RESULTADO), então um container por desfecho — um de cada vez.
// Integrado: só roda com -Pintegrado (docs/testing-strategy.md).
@Tag("integrado")
class PagamentoExternoServiceIntegrationTest {

    private static final int PORTA = 8086;

    @Test
    @Disabled("SUCESSO chama o webhook síncrono antes de responder — volta após o desacoplamento (TODO em PagamentoController de pagamento-externo)")
    void resultadoSucessoDevolveIdTransacao() {
        try (GenericContainer<?> externo = subirPagamentoExterno("SUCESSO")) {
            UUID idTransacao = efetuar(externo).idTransacao();

            assertNotNull(idTransacao);
        }
    }

    @Test
    void resultadoFalhaDeNegocioDevolve409() {
        try (GenericContainer<?> externo = subirPagamentoExterno("FALHA_NEGOCIO")) {
            assertThrows(HttpClientErrorException.Conflict.class, () -> efetuar(externo));
        }
    }

    @Test
    void resultadoFalhaDeInfraDevolve500() {
        try (GenericContainer<?> externo = subirPagamentoExterno("FALHA_INFRA")) {
            assertThrows(HttpServerErrorException.class, () -> efetuar(externo));
        }
    }

    private static GenericContainer<?> subirPagamentoExterno(String resultado) {
        GenericContainer<?> externo = ServicoEmContainer.de("pagamento-externo")
            .withExposedPorts(PORTA)
            .withEnv("SPRING_PROFILES_ACTIVE", "test")
            .withEnv("SIMULACAO_RESULTADO", resultado)
            .waitingFor(Wait.forLogMessage(".*Started PagamentoExternoApplication.*\\n", 1)
                .withStartupTimeout(Duration.ofMinutes(3)));
        externo.start();
        return externo;
    }

    private static EfetuarPagamentoResponse efetuar(GenericContainer<?> externo) {
        // credenciais default de internal-backend.clients de pagamento-externo
        PagamentoExternoService servico = new PagamentoExternoService(
            "http://" + externo.getHost() + ":" + externo.getMappedPort(PORTA),
            "pagamentoExternoId", "pagamentoExternoSecret");
        return servico.efetuar("cartao", new BigDecimal("100.00"), UUID.randomUUID());
    }

}

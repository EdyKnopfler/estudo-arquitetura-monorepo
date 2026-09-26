package com.derso.arquitetura.pagamentointerno;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import com.derso.arquitetura.pagamentointerno.dto.CriarPagamentoRequest;
import com.derso.arquitetura.pagamentointerno.dto.PagamentoDTO;

import com.derso.arquitetura.webbase.teste.ServicoEmContainer;

// Black-box: pagamento-interno e pagamento-externo como containers reais (ServicoEmContainer),
// conversando por HTTP numa network isolada, sem mock. RabbitMQ é exigido pelo boot do profile "web"
// (o /webhook publica a 1ª mensagem da SAGA). Integrado: só roda com -Pintegrado (docs/testing-strategy.md).
@Disabled("fluxo passa pelo webhook síncrono — volta após o desacoplamento (TODO em PagamentoController de pagamento-externo)")
@Tag("integrado")
class PagamentoCriacaoIntegradoTest {

    private static Network network;
    private static PostgreSQLContainer postgres;
    private static RabbitMQContainer rabbit;
    private static GenericContainer<?> pagamentoExterno;
    private static GenericContainer<?> pagamentoInterno;

    @BeforeAll
    static void subirContainers() {
        network = Network.newNetwork();

        postgres = new PostgreSQLContainer("postgres:18.1")
            .withDatabaseName("interno_pagamento")
            .withUsername("app")
            .withPassword("app")
            .withNetwork(network)
            .withNetworkAliases("db");
        postgres.start();

        rabbit = new RabbitMQContainer("rabbitmq:4.2.2-management-alpine")
            .withNetwork(network)
            .withNetworkAliases("broker");
        rabbit.start();

        pagamentoExterno = ServicoEmContainer.de("pagamento-externo")
            .withNetwork(network)
            .withNetworkAliases("pagamento-externo")
            .withEnv("PAGAMENTO_INTERNO_HOST", "pagamento-interno")
            .withEnv("PAGAMENTO_INTERNO_PORT", "8087")
            // profile `test` + desfecho fixo: sem isso /efetuar cai no chaos aleatório (25% de falha)
            .withEnv("SPRING_PROFILES_ACTIVE", "test")
            .withEnv("SIMULACAO_RESULTADO", "SUCESSO")
            .waitingFor(Wait.forLogMessage(".*Started PagamentoExternoApplication.*\\n", 1)
                .withStartupTimeout(Duration.ofMinutes(3)));
        pagamentoExterno.start();

        pagamentoInterno = ServicoEmContainer.de("pagamento-interno")
            .withNetwork(network)
            .withNetworkAliases("pagamento-interno")
            .withExposedPorts(8087)
            .withEnv("SPRING_PROFILES_ACTIVE", "web")
            .withEnv("DB_HOST", "db")
            .withEnv("DB_PORT", "5432")
            .withEnv("DB_USER", "app")
            .withEnv("DB_PASSWORD", "app")
            .withEnv("BROKER_HOST", "broker")
            .withEnv("BROKER_PORT", "5672")
            .withEnv("BROKER_USER", rabbit.getAdminUsername())
            .withEnv("BROKER_PASSWORD", rabbit.getAdminPassword())
            .withEnv("PAGAMENTO_EXTERNO_HOST", "pagamento-externo")
            .withEnv("PAGAMENTO_EXTERNO_PORT", "8086")
            .waitingFor(Wait.forLogMessage(".*Started PagamentoInternoApplication.*\\n", 1)
                .withStartupTimeout(Duration.ofMinutes(3)));
        pagamentoInterno.start();
    }

    @AfterAll
    static void pararContainers() {
        if (pagamentoInterno != null) {
            pagamentoInterno.stop();
        }
        if (pagamentoExterno != null) {
            pagamentoExterno.stop();
        }
        if (rabbit != null) {
            rabbit.stop();
        }
        if (postgres != null) {
            postgres.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    @Test
    void postPagamentosCriaPagamentoDeVerdadeViaPagamentoExternoReal() {
        RestClient restClient = RestClient.builder()
            .baseUrl("http://" + pagamentoInterno.getHost() + ":" + pagamentoInterno.getMappedPort(8087))
            // credenciais default de .env.example (internal-backend.clients de pagamento-interno)
            .defaultHeader("X-Client-Id", "sessaoCompraId")
            .defaultHeader("X-Client-Secret", "sessaoCompraSecret")
            .build();

        CriarPagamentoRequest pedido = new CriarPagamentoRequest(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );

        PagamentoDTO resposta = restClient.post()
            .uri("/pagamentos")
            .contentType(MediaType.APPLICATION_JSON)
            .body(pedido)
            .retrieve()
            .body(PagamentoDTO.class);

        assertNotNull(resposta);
        assertNotNull(resposta.id());
    }

}

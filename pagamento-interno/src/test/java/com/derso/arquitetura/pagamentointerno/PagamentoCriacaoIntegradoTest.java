package com.derso.arquitetura.pagamentointerno;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import com.derso.arquitetura.pagamentointerno.dto.CriarPagamentoRequest;
import com.derso.arquitetura.pagamentointerno.dto.PagamentoDTO;

// Teste black-box de verdade: pagamento-interno e pagamento-externo rodando como containers reais,
// buildados a partir do próprio Dockerfile de cada um (mesmo artefato que rodaria em produção),
// conversando entre si por HTTP dentro de uma network isolada do Testcontainers — sem mock nenhum.
// Só precisa de Postgres + RabbitMQ (RabbitMQ é exigido pelo boot do profile "web" hoje, ver
// SagasWiring/RabbitConfig — não tem a ver com o fluxo de criação de pagamento em si).
//
// Opt-in (SAGAS_TESTCONTAINERS=true), mesma flag de docs/testing-strategy.md — builda 2 imagens
// Maven multi-módulo do zero, é lento de propósito, não roda no `mvn test` default.
//
// Conhecido: o build context enviado pro Docker é o repo inteiro (não respeita .dockerignore, o
// ImageFromDockerfile desta versão do Testcontainers não aplica isso) — correto, só mais pesado
// que precisaria. Otimizar depois se incomodar.
class PagamentoCriacaoIntegradoTest {

    private static Network network;
    private static PostgreSQLContainer postgres;
    private static RabbitMQContainer rabbit;
    private static GenericContainer<?> pagamentoExterno;
    private static GenericContainer<?> pagamentoInterno;

    @BeforeAll
    static void subirContainers() {
        Assumptions.assumeTrue(
            "true".equalsIgnoreCase(System.getenv("SAGAS_TESTCONTAINERS")),
            "Requer SAGAS_TESTCONTAINERS=true — sobe Postgres, RabbitMQ, pagamento-interno e "
                + "pagamento-externo como containers reais e builda as imagens a partir dos "
                + "Dockerfiles. Lento de propósito, não roda no mvn test default."
        );

        Path repoRoot = Paths.get("").toAbsolutePath().getParent();

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

        pagamentoExterno = new GenericContainer<>(
            new ImageFromDockerfile()
                .withFileFromPath(".", repoRoot)
                .withDockerfilePath("pagamento-externo/Dockerfile")
        )
            .withNetwork(network)
            .withNetworkAliases("pagamento-externo")
            .withEnv("PAGAMENTO_INTERNO_HOST", "pagamento-interno")
            .withEnv("PAGAMENTO_INTERNO_PORT", "8087")
            .waitingFor(Wait.forLogMessage(".*Started PagamentoExternoApplication.*\\n", 1)
                .withStartupTimeout(Duration.ofMinutes(3)));
        pagamentoExterno.start();

        pagamentoInterno = new GenericContainer<>(
            new ImageFromDockerfile()
                .withFileFromPath(".", repoRoot)
                .withDockerfilePath("pagamento-interno/Dockerfile")
        )
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

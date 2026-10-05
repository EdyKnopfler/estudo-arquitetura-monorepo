package com.derso.arquitetura.pagamentointerno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.derso.arquitetura.pagamentointerno.dto.CriarTransacaoResponse;
import com.derso.arquitetura.webbase.teste.ServicoEmContainer;

// Contrato HTTP real com pagamento-externo em container (ServicoEmContainer). Desfecho do simulador é
// config do processo (SIMULACAO_RESULTADO), então um container por desfecho — um de cada vez.
// Integrado: só roda com -Pintegrado (docs/testing-strategy.md).
@Tag("integrado")
class PagamentoExternoServiceIntegrationTest {

    private static final int PORTA = 8086;

    private static Network network;
    private static PostgreSQLContainer postgres;

    @BeforeAll
    static void subirBanco() {
        network = Network.newNetwork();

        postgres = new PostgreSQLContainer("postgres:18.1")
            .withDatabaseName("externo_pagamento")
            .withUsername("app")
            .withPassword("app")
            .withNetwork(network)
            .withNetworkAliases("db");
        postgres.start();
    }

    @AfterAll
    static void pararBanco() {
        if (postgres != null) {
            postgres.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    @Test
    void sucessoDevolveTransacaoPendenteComUrlEMesmaChaveDevolveAMesma() {
        try (GenericContainer<?> externo = subirPagamentoExterno("SUCESSO")) {
            UUID chave = UUID.randomUUID();

            CriarTransacaoResponse primeira = criar(externo, chave);
            CriarTransacaoResponse segunda = criar(externo, chave);

            assertNotNull(primeira.idTransacao());
            assertNotNull(primeira.urlPagamento());
            assertEquals("PENDENTE", primeira.status());
            assertEquals(primeira.idTransacao(), segunda.idTransacao());
            assertEquals(1, transacoesComChave(chave));
        }
    }

    @Test
    void falhaAntesDeGravarDevolve500SemTransacao() {
        try (GenericContainer<?> externo = subirPagamentoExterno("FALHA_ANTES_DE_GRAVAR")) {
            UUID chave = UUID.randomUUID();

            assertThrows(HttpServerErrorException.class, () -> criar(externo, chave));

            assertEquals(0, transacoesComChave(chave));
        }
    }

    // a falha ambígua: o 500 esconde uma transação criada, que a mesma chave recupera
    @Test
    void falhaDepoisDeGravarDevolve500ComTransacaoCriada() {
        try (GenericContainer<?> externo = subirPagamentoExterno("FALHA_DEPOIS_DE_GRAVAR")) {
            UUID chave = UUID.randomUUID();

            assertThrows(HttpServerErrorException.class, () -> criar(externo, chave));

            assertEquals(1, transacoesComChave(chave));
        }
    }

    private static GenericContainer<?> subirPagamentoExterno(String resultado) {
        GenericContainer<?> externo = ServicoEmContainer.de("pagamento-externo")
            .withNetwork(network)
            .withExposedPorts(PORTA)
            .withEnv("DB_HOST", "db")
            .withEnv("DB_PORT", "5432")
            .withEnv("SPRING_PROFILES_ACTIVE", "test")
            .withEnv("SIMULACAO_RESULTADO", resultado)
            .waitingFor(Wait.forLogMessage(".*Started PagamentoExternoApplication.*\\n", 1)
                .withStartupTimeout(Duration.ofMinutes(3)));
        externo.start();
        return externo;
    }

    private static CriarTransacaoResponse criar(GenericContainer<?> externo, UUID chave) {
        // credenciais default de internal-backend.clients de pagamento-externo
        PagamentoExternoService servico = new PagamentoExternoService(
            "http://" + externo.getHost() + ":" + externo.getMappedPort(PORTA),
            "pagamentoExternoId", "pagamentoExternoSecret");
        return servico.criar("cartao", new BigDecimal("100.00"), chave);
    }

    private static int transacoesComChave(UUID chave) {
        try (Connection conexao = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             PreparedStatement consulta = conexao.prepareStatement("select count(*) from pagamentos where chave_idempotencia = ?")) {
            consulta.setObject(1, chave);
            try (ResultSet resultado = consulta.executeQuery()) {
                resultado.next();
                return resultado.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

}

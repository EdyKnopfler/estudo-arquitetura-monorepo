package com.derso.arquitetura.reservasinterno;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.derso.arquitetura.webbase.teste.ServicoEmContainer;

// Contrato HTTP real com reservas-externo em container (ServicoEmContainer). Chaos desligado
// via SIMULACAO_RESULTADO (a requisição parte do ReservasExternoService, não do teste). Os 404 daqui
// são o que ReservasSagas trata como falha de negócio. Integrado: só roda com -Pintegrado.
@Tag("integrado")
class ReservasExternoServiceIntegrationTest {

    private static final int PORTA = 8082;

    private static Network network;
    private static PostgreSQLContainer postgres;
    private static GenericContainer<?> reservasExterno;
    private static ReservasExternoService servico;

    @BeforeAll
    static void subirReservasExterno() {
        network = Network.newNetwork();

        postgres = new PostgreSQLContainer("postgres:18.1")
            .withDatabaseName("externo_hotel")
            .withUsername("app")
            .withPassword("app")
            .withNetwork(network)
            .withNetworkAliases("db");
        postgres.start();

        reservasExterno = ServicoEmContainer.de("reservas-externo")
            .withNetwork(network)
            .withExposedPorts(PORTA)
            .withEnv("SPRING_PROFILES_ACTIVE", "hotel,test")
            .withEnv("SIMULACAO_RESULTADO", "SUCESSO")
            .withEnv("RESERVAS_EXTERNO_HOTEL_PORT", String.valueOf(PORTA))
            .withEnv("DB_HOST", "db")
            .withEnv("DB_PORT", "5432")
            .waitingFor(Wait.forLogMessage(".*Started ReservasExternoApplication.*\\n", 1)
                .withStartupTimeout(Duration.ofMinutes(3)));
        reservasExterno.start();

        // credenciais default de internal-backend.clients de reservas-externo (application-hotel.yaml)
        servico = new ReservasExternoService(
            "http://" + reservasExterno.getHost() + ":" + reservasExterno.getMappedPort(PORTA),
            "reservasExternoHotelId", "reservasExternoHotelSecret");
    }

    @AfterAll
    static void pararContainers() {
        if (reservasExterno != null) {
            reservasExterno.stop();
        }
        if (postgres != null) {
            postgres.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    @Test
    void criarEConfirmarDentroDoPrazo() {
        UUID idExterno = servico.criar(UUID.randomUUID());

        assertNotNull(idExterno);
        assertDoesNotThrow(() -> servico.confirmar(idExterno));
    }

    @Test
    void confirmarDuasVezesDevolve404NaSegunda() {
        UUID idExterno = servico.criar(UUID.randomUUID());
        servico.confirmar(idExterno);

        assertThrows(HttpClientErrorException.NotFound.class, () -> servico.confirmar(idExterno));
    }

    @Test
    void confirmarInexistenteDevolve404() {
        assertThrows(HttpClientErrorException.NotFound.class, () -> servico.confirmar(UUID.randomUUID()));
    }

    @Test
    void cancelarNaoConfirmadaFunciona() {
        UUID idExterno = servico.criar(UUID.randomUUID());

        assertDoesNotThrow(() -> servico.cancelar(idExterno));
    }

    @Test
    void cancelarConfirmadaDevolve404() {
        UUID idExterno = servico.criar(UUID.randomUUID());
        servico.confirmar(idExterno);

        assertThrows(HttpClientErrorException.NotFound.class, () -> servico.cancelar(idExterno));
    }

}

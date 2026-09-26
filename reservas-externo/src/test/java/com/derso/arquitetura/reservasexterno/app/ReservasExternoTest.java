package com.derso.arquitetura.reservasexterno.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.derso.arquitetura.webbase.teste.PostgresTestcontainersConfig;
import com.derso.arquitetura.reservasexterno.app.dto.CriacaoReservaResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "hotel", "test" })
@Import(PostgresTestcontainersConfig.class)
class ReservasExternoTest {

    // defaults de application-hotel.yaml (internal-backend.clients)
    private static final String CLIENT_ID = "reservasExternoHotelId";
    private static final String CLIENT_SECRET = "reservasExternoHotelSecret";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<UUID> reservasCriadas = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void apagaReservasCriadas() {
        reservasCriadas.forEach(id -> jdbc.update("delete from reservas where id = ?", id));
    }

    @Test
    void criarGravaReservaNaoConfirmadaDoCliente() throws Exception {
        UUID idCliente = UUID.randomUUID();

        UUID id = criar(idCliente);

        Map<String, Object> linha = linha(id);
        assertEquals(idCliente, linha.get("id_cliente"));
        assertEquals(false, linha.get("confirmado"));
        assertNotNull(linha.get("criacao"));
    }

    @Test
    void criarComFalhaDeInfraRetorna500SemGravar() throws Exception {
        UUID idCliente = UUID.randomUUID();

        mockMvc.perform(comoCliente(post("/reservas"), "FALHA_INFRA")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idCliente\":\"" + idCliente + "\"}"))
            .andExpect(status().isInternalServerError());

        assertEquals(0, jdbc.queryForObject("select count(*) from reservas where id_cliente = ?", Integer.class, idCliente));
    }

    @Test
    void criarSemIdClienteRetorna400() throws Exception {
        mockMvc.perform(comoCliente(post("/reservas"), "SUCESSO")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void confirmarDentroDoPrazoMarcaConfirmado() throws Exception {
        UUID id = criar(UUID.randomUUID());

        mockMvc.perform(comoCliente(put("/reservas/confirmar/" + id), "SUCESSO")).andExpect(status().isOk());

        assertEquals(true, linha(id).get("confirmado"));
    }

    @Test
    void confirmarComFalhaDeInfraRetorna500SemConfirmar() throws Exception {
        UUID id = criar(UUID.randomUUID());

        mockMvc.perform(comoCliente(put("/reservas/confirmar/" + id), "FALHA_INFRA"))
            .andExpect(status().isInternalServerError());

        assertEquals(false, linha(id).get("confirmado"));
    }

    @Test
    void confirmarReservaExpiradaRetorna404() throws Exception {
        // criacao é gravada em UTC (hibernate.jdbc.time_zone)
        UUID id = inserirDireto(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(16), false);

        mockMvc.perform(comoCliente(put("/reservas/confirmar/" + id), "SUCESSO")).andExpect(status().isNotFound());

        assertEquals(false, linha(id).get("confirmado"));
    }

    @Test
    void confirmarDuasVezesRetorna404NaSegunda() throws Exception {
        UUID id = criar(UUID.randomUUID());

        mockMvc.perform(comoCliente(put("/reservas/confirmar/" + id), "SUCESSO")).andExpect(status().isOk());
        mockMvc.perform(comoCliente(put("/reservas/confirmar/" + id), "SUCESSO")).andExpect(status().isNotFound());
    }

    @Test
    void confirmarInexistenteRetorna404() throws Exception {
        mockMvc.perform(comoCliente(put("/reservas/confirmar/" + UUID.randomUUID()), "SUCESSO"))
            .andExpect(status().isNotFound());
    }

    @Test
    void removerNaoConfirmadaApagaALinha() throws Exception {
        UUID id = criar(UUID.randomUUID());

        mockMvc.perform(comoCliente(delete("/reservas/" + id), "SUCESSO")).andExpect(status().isOk());

        assertTrue(jdbc.queryForList("select id from reservas where id = ?", id).isEmpty());
    }

    @Test
    void removerConfirmadaRetorna404EMantemALinha() throws Exception {
        UUID id = inserirDireto(LocalDateTime.now(ZoneOffset.UTC), true);

        mockMvc.perform(comoCliente(delete("/reservas/" + id), "SUCESSO")).andExpect(status().isNotFound());

        assertFalse(jdbc.queryForList("select id from reservas where id = ?", id).isEmpty());
    }

    @Test
    void semCredencialRetorna401() throws Exception {
        mockMvc.perform(post("/reservas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idCliente\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    private UUID criar(UUID idCliente) throws Exception {
        String corpo = mockMvc.perform(comoCliente(post("/reservas"), "SUCESSO")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idCliente\":\"" + idCliente + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        UUID id = objectMapper.readValue(corpo, CriacaoReservaResponse.class).idReserva();
        reservasCriadas.add(id);
        return id;
    }

    private UUID inserirDireto(LocalDateTime criacao, boolean confirmado) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into reservas (id, id_cliente, criacao, confirmado) values (?, ?, ?, ?)",
            id, UUID.randomUUID(), Timestamp.valueOf(criacao), confirmado);
        reservasCriadas.add(id);
        return id;
    }

    private Map<String, Object> linha(UUID id) {
        return jdbc.queryForMap("select * from reservas where id = ?", id);
    }

    private static MockHttpServletRequestBuilder comoCliente(MockHttpServletRequestBuilder req, String resultadoSimulado) {
        return req
            .header("X-Client-Id", CLIENT_ID)
            .header("X-Client-Secret", CLIENT_SECRET)
            .header(ReservasController.HEADER_SIMULAR_RESULTADO, resultadoSimulado);
    }

}

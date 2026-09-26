package com.derso.arquitetura.reservasinterno.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.derso.arquitetura.webbase.teste.PostgresTestcontainersConfig;
import com.derso.arquitetura.reservasinterno.ReservasExternoService;
import com.derso.arquitetura.reservasinterno.dto.ReservaDTO;
import com.fasterxml.jackson.databind.ObjectMapper;

// Fronteira HTTP com reservas-externo mockada — contrato real em ReservasExternoServiceIntegrationTest.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "hotel", "web", "test" })
@Import(PostgresTestcontainersConfig.class)
class ReservasInternoWebTest {

    // defaults de application-hotel.yaml (internal-backend.clients)
    private static final String CLIENT_ID = "reservasInternoHotelId";
    private static final String CLIENT_SECRET = "reservasInternoHotelSecret";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<UUID> reservasCriadas = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ReservasExternoService externo;

    @AfterEach
    void apagaReservasCriadas() {
        reservasCriadas.forEach(id -> jdbc.update("delete from reservas where id = ?", id));
    }

    @Test
    void criarGravaReservaComIdExternoEDevolveSoOIdInterno() throws Exception {
        UUID cliente = UUID.randomUUID();
        UUID idExterno = UUID.randomUUID();
        when(externo.criar(cliente)).thenReturn(idExterno);

        UUID id = criar(cliente);

        assertEquals(idExterno, idExternoGravado(id));
        assertNotEquals(idExterno, id);
    }

    @Test
    void falhaDoExternoAoCriarNaoGravaNada() throws Exception {
        when(externo.criar(any())).thenThrow(new RuntimeException("reservas-externo fora"));
        int antes = totalDeReservas();

        requisicao(post("/reservas"), CLIENT_SECRET, "{\"idCliente\":\"" + UUID.randomUUID() + "\"}")
            .andExpect(status().is5xxServerError());

        assertEquals(antes, totalDeReservas());
    }

    @Test
    void trocarCriaNovaCancelaAAntigaNoExternoEApagaALinhaAntiga() throws Exception {
        UUID cliente = UUID.randomUUID();
        UUID externoAntigo = UUID.randomUUID();
        UUID externoNovo = UUID.randomUUID();
        when(externo.criar(cliente)).thenReturn(externoAntigo);
        UUID antiga = criar(cliente);
        when(externo.criar(cliente)).thenReturn(externoNovo);

        UUID nova = trocar(antiga, cliente);

        verify(externo).cancelar(externoAntigo);
        assertEquals(externoNovo, idExternoGravado(nova));
        assertEquals(0, contar(antiga));
    }

    @Test
    void trocarComCancelamentoFalhandoDevolveANovaEMantemAAntiga_melhorEsforco() throws Exception {
        UUID cliente = UUID.randomUUID();
        UUID externoAntigo = UUID.randomUUID();
        when(externo.criar(cliente)).thenReturn(externoAntigo);
        UUID antiga = criar(cliente);
        when(externo.criar(cliente)).thenReturn(UUID.randomUUID());
        doThrow(new RuntimeException("reservas-externo fora")).when(externo).cancelar(externoAntigo);

        UUID nova = trocar(antiga, cliente);

        assertEquals(1, contar(nova));
        assertEquals(1, contar(antiga));
    }

    @Test
    void trocarReservaInexistenteRetorna404SemChamarExterno() throws Exception {
        requisicao(put("/reservas/" + UUID.randomUUID() + "/trocar"), CLIENT_SECRET,
                "{\"idCliente\":\"" + UUID.randomUUID() + "\"}")
            .andExpect(status().isNotFound());

        verify(externo, never()).criar(any());
    }

    @Test
    void corpoSemIdClienteRetorna400SemChamarExterno() throws Exception {
        requisicao(post("/reservas"), CLIENT_SECRET, "{}").andExpect(status().isBadRequest());

        verify(externo, never()).criar(any());
    }

    @Test
    void secretErradoRetorna401SemChamarExterno() throws Exception {
        requisicao(post("/reservas"), "secret-errado", "{\"idCliente\":\"" + UUID.randomUUID() + "\"}")
            .andExpect(status().isUnauthorized());

        verify(externo, never()).criar(any());
    }

    private UUID criar(UUID cliente) throws Exception {
        String corpo = requisicao(post("/reservas"), CLIENT_SECRET, "{\"idCliente\":\"" + cliente + "\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idExterno").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        return registrar(corpo);
    }

    private UUID trocar(UUID antiga, UUID cliente) throws Exception {
        String corpo = requisicao(put("/reservas/" + antiga + "/trocar"), CLIENT_SECRET, "{\"idCliente\":\"" + cliente + "\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idExterno").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        return registrar(corpo);
    }

    private UUID registrar(String corpo) throws Exception {
        UUID id = objectMapper.readValue(corpo, ReservaDTO.class).id();
        reservasCriadas.add(id);
        return id;
    }

    private UUID idExternoGravado(UUID id) {
        return jdbc.queryForObject("select id_externo from reservas where id = ?", UUID.class, id);
    }

    private int contar(UUID id) {
        return jdbc.queryForObject("select count(*) from reservas where id = ?", Integer.class, id);
    }

    private int totalDeReservas() {
        return jdbc.queryForObject("select count(*) from reservas", Integer.class);
    }

    private ResultActions requisicao(MockHttpServletRequestBuilder req, String secret, String corpo) throws Exception {
        return mockMvc.perform(req
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Client-Id", CLIENT_ID)
            .header("X-Client-Secret", secret)
            .content(corpo));
    }

}

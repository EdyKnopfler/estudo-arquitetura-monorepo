package com.derso.arquitetura.pagamentointerno.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import com.derso.arquitetura.webbase.teste.PostgresTestcontainersConfig;
import com.derso.arquitetura.pagamentointerno.PagamentoExternoService;
import com.derso.arquitetura.pagamentointerno.dto.CriarPagamentoRequest;
import com.derso.arquitetura.pagamentointerno.dto.PagamentoDTO;
import com.derso.arquitetura.sagas.RabbitMQTestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

// Fronteira HTTP com pagamento-externo mockada — contrato real em PagamentoExternoServiceIntegrationTest.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "web", "test" })
@Import({ PostgresTestcontainersConfig.class, RabbitMQTestcontainersConfig.class })
class PagamentoCriacaoTest {

    // defaults de application.yaml (internal-backend.clients)
    private static final String CLIENT_ID = "sessaoCompraId";
    private static final String CLIENT_SECRET = "sessaoCompraSecret";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<UUID> sessoesUsadas = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private PagamentoExternoService externo;

    @AfterEach
    void apagaPagamentosCriados() {
        sessoesUsadas.forEach(id -> jdbc.update("delete from pagamentos where id_sessao_compra = ?", id));
    }

    @Test
    void criarGravaLinhaComTodosOsIdsEDevolveSoOIdInterno() throws Exception {
        UUID idExterno = UUID.randomUUID();
        when(externo.efetuar(anyString(), any(BigDecimal.class))).thenReturn(idExterno);
        CriarPagamentoRequest pedido = novoPedido();

        String corpo = mockMvc.perform(comCredencial(post("/pagamentos"), CLIENT_SECRET).content(json(pedido)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idExterno").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        UUID id = objectMapper.readValue(corpo, PagamentoDTO.class).id();

        Map<String, Object> linha = jdbc.queryForMap("select * from pagamentos where id = ?", id);
        assertEquals(idExterno, linha.get("id_externo"));
        assertEquals(pedido.idSessaoCompra(), linha.get("id_sessao_compra"));
        assertEquals(pedido.idReservaHotel(), linha.get("id_reserva_hotel"));
        assertEquals(pedido.idReservaVooIda(), linha.get("id_reserva_voo_ida"));
        assertEquals(pedido.idReservaVooVolta(), linha.get("id_reserva_voo_volta"));
        assertNotEquals(idExterno, id);
    }

    @Test
    void falhaDeInfraNoExternoNaoGravaNada() throws Exception {
        when(externo.efetuar(anyString(), any(BigDecimal.class))).thenThrow(
            HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], null));
        CriarPagamentoRequest pedido = novoPedido();

        mockMvc.perform(comCredencial(post("/pagamentos"), CLIENT_SECRET).content(json(pedido)))
            .andExpect(status().is5xxServerError());

        assertEquals(0, linhasDaSessao(pedido.idSessaoCompra()));
    }

    @Test
    void recusaDeNegocioNoExternoNaoGravaNada() throws Exception {
        when(externo.efetuar(anyString(), any(BigDecimal.class))).thenThrow(
            HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, new byte[0], null));
        CriarPagamentoRequest pedido = novoPedido();

        mockMvc.perform(comCredencial(post("/pagamentos"), CLIENT_SECRET).content(json(pedido)))
            .andExpect(status().is5xxServerError());

        assertEquals(0, linhasDaSessao(pedido.idSessaoCompra()));
    }

    @Test
    void corpoComCampoNuloRetorna400SemChamarExterno() throws Exception {
        String semReservaHotel = "{\"idSessaoCompra\":\"" + UUID.randomUUID()
            + "\",\"idReservaVooIda\":\"" + UUID.randomUUID()
            + "\",\"idReservaVooVolta\":\"" + UUID.randomUUID() + "\"}";

        mockMvc.perform(comCredencial(post("/pagamentos"), CLIENT_SECRET).content(semReservaHotel))
            .andExpect(status().isBadRequest());

        verify(externo, never()).efetuar(anyString(), any(BigDecimal.class));
    }

    @Test
    void secretErradoRetorna401SemChamarExterno() throws Exception {
        mockMvc.perform(comCredencial(post("/pagamentos"), "secret-errado").content(json(novoPedido())))
            .andExpect(status().isUnauthorized());

        verify(externo, never()).efetuar(anyString(), any(BigDecimal.class));
    }

    @Test
    void semCredencialRetorna401() throws Exception {
        mockMvc.perform(post("/pagamentos").contentType(MediaType.APPLICATION_JSON).content(json(novoPedido())))
            .andExpect(status().isUnauthorized());
    }

    private CriarPagamentoRequest novoPedido() {
        CriarPagamentoRequest pedido = new CriarPagamentoRequest(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        sessoesUsadas.add(pedido.idSessaoCompra());
        return pedido;
    }

    private int linhasDaSessao(UUID idSessaoCompra) {
        return jdbc.queryForObject("select count(*) from pagamentos where id_sessao_compra = ?", Integer.class, idSessaoCompra);
    }

    private String json(Object objeto) throws Exception {
        return objectMapper.writeValueAsString(objeto);
    }

    private static MockHttpServletRequestBuilder comCredencial(MockHttpServletRequestBuilder req, String secret) {
        return req
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Client-Id", CLIENT_ID)
            .header("X-Client-Secret", secret);
    }

}

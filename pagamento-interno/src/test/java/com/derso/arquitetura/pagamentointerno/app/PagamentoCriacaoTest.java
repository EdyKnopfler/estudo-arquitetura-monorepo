package com.derso.arquitetura.pagamentointerno.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.derso.arquitetura.webbase.teste.PostgresTestcontainersConfig;
import com.derso.arquitetura.pagamentointerno.PagamentoExternoService;
import com.derso.arquitetura.pagamentointerno.dto.CriarPagamentoRequest;
import com.derso.arquitetura.pagamentointerno.dto.EfetuarPagamentoResponse;
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
    void sucessoGravaLinhaAguardandoEDevolveIdInternoEUrl() throws Exception {
        UUID idSessao = novaSessao();
        CriarPagamentoRequest pedido = novoPedido();
        EfetuarPagamentoResponse resposta = respostaDoGateway();
        when(externo.efetuar(anyString(), any(BigDecimal.class), any(UUID.class))).thenReturn(resposta);

        PagamentoDTO dto = criar(idSessao, pedido)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idExterno").doesNotExist())
            .andReturn().getResponse().getContentAsString().transform(this::lerDTO);

        Map<String, Object> linha = linhaDaSessao(idSessao);
        assertEquals(dto.id(), linha.get("id"));
        assertEquals("AGUARDANDO_PAGAMENTO", linha.get("status"));
        assertEquals(resposta.idTransacao(), linha.get("id_externo"));
        assertEquals(resposta.urlPagamento(), linha.get("url_pagamento"));
        assertEquals(resposta.urlPagamento(), dto.urlPagamento());
        assertEquals(pedido.idReservaHotel(), linha.get("id_reserva_hotel"));
        assertEquals(pedido.idReservaVooIda(), linha.get("id_reserva_voo_ida"));
        assertEquals(pedido.idReservaVooVolta(), linha.get("id_reserva_voo_volta"));
        assertNotEquals(resposta.idTransacao(), dto.id());
    }

    @Test
    void comUrlJaObtidaDevolveAMesmaSemChamarOGateway() throws Exception {
        UUID idSessao = novaSessao();
        when(externo.efetuar(anyString(), any(BigDecimal.class), any(UUID.class))).thenReturn(respostaDoGateway());

        String primeira = criar(idSessao, novoPedido()).andReturn().getResponse().getContentAsString();
        String segunda = criar(idSessao, novoPedido())
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertEquals(lerDTO(primeira), lerDTO(segunda));
        verify(externo, times(1)).efetuar(anyString(), any(BigDecimal.class), any(UUID.class));
    }

    @Test
    void falhaAmbiguaMantemCriandoEARetentativaRepeteAMesmaChave() throws Exception {
        UUID idSessao = novaSessao();
        when(externo.efetuar(anyString(), any(BigDecimal.class), any(UUID.class)))
            .thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], null))
            .thenReturn(respostaDoGateway());

        criar(idSessao, novoPedido()).andExpect(status().is5xxServerError());
        Map<String, Object> depoisDaFalha = linhaDaSessao(idSessao);
        assertEquals("CRIANDO", depoisDaFalha.get("status"));
        assertNull(depoisDaFalha.get("url_pagamento"));

        criar(idSessao, novoPedido()).andExpect(status().isOk());

        List<UUID> chaves = chavesEnviadas(2);
        assertEquals(chaves.get(0), chaves.get(1));
        assertEquals(depoisDaFalha.get("chave_idempotencia"), chaves.get(0));
    }

    @Test
    void recusaDevolve409MantemCriandoEARetentativaUsaChaveNova() throws Exception {
        UUID idSessao = novaSessao();
        when(externo.efetuar(anyString(), any(BigDecimal.class), any(UUID.class)))
            .thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, new byte[0], null))
            .thenReturn(respostaDoGateway());

        criar(idSessao, novoPedido()).andExpect(status().isConflict());
        assertEquals("CRIANDO", linhaDaSessao(idSessao).get("status"));

        criar(idSessao, novoPedido()).andExpect(status().isOk());

        List<UUID> chaves = chavesEnviadas(2);
        assertNotEquals(chaves.get(0), chaves.get(1));
    }

    @Test
    void corpoComCampoNuloRetorna400SemChamarExterno() throws Exception {
        String semReservaHotel = "{\"idReservaVooIda\":\"" + UUID.randomUUID()
            + "\",\"idReservaVooVolta\":\"" + UUID.randomUUID() + "\"}";

        mockMvc.perform(comCredencial(put("/pagamentos/{id}", novaSessao()), CLIENT_SECRET).content(semReservaHotel))
            .andExpect(status().isBadRequest());

        verify(externo, never()).efetuar(anyString(), any(BigDecimal.class), any(UUID.class));
    }

    @Test
    void secretErradoRetorna401SemChamarExterno() throws Exception {
        mockMvc.perform(comCredencial(put("/pagamentos/{id}", novaSessao()), "secret-errado").content(json(novoPedido())))
            .andExpect(status().isUnauthorized());

        verify(externo, never()).efetuar(anyString(), any(BigDecimal.class), any(UUID.class));
    }

    @Test
    void semCredencialRetorna401() throws Exception {
        mockMvc.perform(put("/pagamentos/{id}", novaSessao()).contentType(MediaType.APPLICATION_JSON).content(json(novoPedido())))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void timeoutMantemCriandoEARetentativaRepeteAMesmaChave() throws Exception {
        UUID idSessao = novaSessao();
        when(externo.efetuar(anyString(), any(BigDecimal.class), any(UUID.class)))
            .thenThrow(new ResourceAccessException("Read timed out"))
            .thenReturn(respostaDoGateway());

        criar(idSessao, novoPedido()).andExpect(status().is5xxServerError());
        assertEquals("CRIANDO", linhaDaSessao(idSessao).get("status"));

        criar(idSessao, novoPedido()).andExpect(status().isOk());

        List<UUID> chaves = chavesEnviadas(2);
        assertEquals(chaves.get(0), chaves.get(1));
    }

    @Test
    void respostaSemUrlMantemCriandoEARetentativaRepeteAMesmaChave() throws Exception {
        UUID idSessao = novaSessao();
        when(externo.efetuar(anyString(), any(BigDecimal.class), any(UUID.class)))
            .thenReturn(new EfetuarPagamentoResponse(UUID.randomUUID(), "processando", null))
            .thenReturn(respostaDoGateway());

        criar(idSessao, novoPedido()).andExpect(status().is5xxServerError());
        Map<String, Object> depoisDaFalha = linhaDaSessao(idSessao);
        assertEquals("CRIANDO", depoisDaFalha.get("status"));
        assertNull(depoisDaFalha.get("id_externo"));

        criar(idSessao, novoPedido()).andExpect(status().isOk());

        List<UUID> chaves = chavesEnviadas(2);
        assertEquals(chaves.get(0), chaves.get(1));
    }

    @Test
    void putsConcorrentesDaMesmaSessaoGravamUmaLinhaEDevolvemOMesmoPagamento() throws Exception {
        UUID idSessao = novaSessao();
        EfetuarPagamentoResponse resposta = respostaDoGateway();
        // os dois só saem do gateway depois de ambos terem lido a linha em CRIANDO
        CountDownLatch ambosNoGateway = new CountDownLatch(2);
        when(externo.efetuar(anyString(), any(BigDecimal.class), any(UUID.class))).thenAnswer(invocacao -> {
            ambosNoGateway.countDown();
            if (!ambosNoGateway.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("segunda chamada não chegou ao gateway");
            }
            return resposta;
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> a = pool.submit(() -> criar(idSessao, novoPedido()).andReturn());
            Future<MvcResult> b = pool.submit(() -> criar(idSessao, novoPedido()).andReturn());
            MvcResult resultadoA = a.get(20, TimeUnit.SECONDS);
            MvcResult resultadoB = b.get(20, TimeUnit.SECONDS);

            assertEquals(200, resultadoA.getResponse().getStatus());
            assertEquals(200, resultadoB.getResponse().getStatus());
            assertEquals(lerDTO(resultadoA.getResponse().getContentAsString()), lerDTO(resultadoB.getResponse().getContentAsString()));
        } finally {
            pool.shutdownNow();
        }

        List<UUID> chaves = chavesEnviadas(2);
        assertEquals(chaves.get(0), chaves.get(1));
        assertEquals(1, jdbc.queryForObject("select count(*) from pagamentos where id_sessao_compra = ?", Integer.class, idSessao));
    }

    @Test
    void tentativaTrocadaDuranteACriacaoFalhaSemRegistrarAUrlDaAntiga() throws Exception {
        UUID idSessao = novaSessao();
        CountDownLatch primeiraNoGateway = new CountDownLatch(1);
        CountDownLatch liberaPrimeira = new CountDownLatch(1);
        AtomicInteger chamadas = new AtomicInteger();
        when(externo.efetuar(anyString(), any(BigDecimal.class), any(UUID.class))).thenAnswer(invocacao -> {
            if (chamadas.incrementAndGet() == 1) {
                primeiraNoGateway.countDown();
                liberaPrimeira.await(10, TimeUnit.SECONDS);
                return respostaDoGateway();
            }
            throw HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, new byte[0], null);
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> primeira = pool.submit(() -> criar(idSessao, novoPedido()).andReturn());
            primeiraNoGateway.await(10, TimeUnit.SECONDS);

            // segunda chamada é recusada e troca a chave enquanto a primeira ainda espera o gateway
            criar(idSessao, novoPedido()).andExpect(status().isConflict());
            liberaPrimeira.countDown();

            assertEquals(500, primeira.get(20, TimeUnit.SECONDS).getResponse().getStatus());
        } finally {
            pool.shutdownNow();
        }

        List<UUID> chaves = chavesEnviadas(2);
        Map<String, Object> linha = linhaDaSessao(idSessao);
        assertEquals("CRIANDO", linha.get("status"));
        assertNull(linha.get("url_pagamento"));
        assertNotEquals(chaves.get(0), linha.get("chave_idempotencia"));
    }

    private ResultActions criar(UUID idSessao, CriarPagamentoRequest pedido) throws Exception {
        return mockMvc.perform(comCredencial(put("/pagamentos/{id}", idSessao), CLIENT_SECRET).content(json(pedido)));
    }

    private List<UUID> chavesEnviadas(int chamadas) {
        ArgumentCaptor<UUID> chave = ArgumentCaptor.forClass(UUID.class);
        verify(externo, times(chamadas)).efetuar(anyString(), any(BigDecimal.class), chave.capture());
        return chave.getAllValues();
    }

    private UUID novaSessao() {
        UUID id = UUID.randomUUID();
        sessoesUsadas.add(id);
        return id;
    }

    private static CriarPagamentoRequest novoPedido() {
        return new CriarPagamentoRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private static EfetuarPagamentoResponse respostaDoGateway() {
        UUID idTransacao = UUID.randomUUID();
        return new EfetuarPagamentoResponse(idTransacao, "processando", "http://gateway/pagar/" + idTransacao);
    }

    private Map<String, Object> linhaDaSessao(UUID idSessao) {
        return jdbc.queryForMap("select * from pagamentos where id_sessao_compra = ?", idSessao);
    }

    private PagamentoDTO lerDTO(String corpo) {
        try {
            return objectMapper.readValue(corpo, PagamentoDTO.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

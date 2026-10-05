package com.derso.arquitetura.pagamentoexterno.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.derso.arquitetura.pagamentoexterno.webhook.WebhookService;
import com.derso.arquitetura.webbase.teste.PostgresTestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = { "simulacao.resultado=SUCESSO", "simulacao.resultado-pagar=ACEITO" })
@Import(PostgresTestcontainersConfig.class)
class PagamentoExternoTest {

    // defaults de application.yaml (internal-backend.clients)
    private static final String CLIENT_ID = "pagamentoExternoId";
    private static final String CLIENT_SECRET = "pagamentoExternoSecret";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<UUID> chavesUsadas = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private WebhookService webhook;

    @AfterEach
    void apagaPagamentosCriados() {
        chavesUsadas.forEach(chave -> jdbc.update("delete from pagamentos where chave_idempotencia = ?", chave));
    }

    @Test
    void criarGravaPendenteDoClienteEDevolveUrl() throws Exception {
        PagamentoResponseDTO resposta = criarComSucesso(novaChave());

        assertEquals("PENDENTE", resposta.status());
        assertEquals("http://localhost:8086/pagar/" + resposta.idTransacao(), resposta.urlPagamento());

        Map<String, Object> linha = linha(resposta.idTransacao());
        assertEquals(CLIENT_ID, linha.get("id_cliente"));
        assertEquals("cartao", linha.get("metodo"));
        assertEquals(0, new BigDecimal("100.00").compareTo((BigDecimal) linha.get("valor")));
        assertEquals("PENDENTE", linha.get("status"));
    }

    @Test
    void mesmaChaveDevolveAMesmaTransacaoSemCriarOutra() throws Exception {
        UUID chave = novaChave();

        UUID primeira = criarComSucesso(chave).idTransacao();
        UUID segunda = criarComSucesso(chave).idTransacao();

        assertEquals(primeira, segunda);
        assertEquals(1, contarPorChave(chave));
    }

    @Test
    void mesmaChaveDeOutroClienteNaoColide() throws Exception {
        UUID chave = novaChave();
        UUID deOutroCliente = inserirDireto("outroCliente", chave, LocalDateTime.now(ZoneOffset.UTC));

        assertNotEquals(deOutroCliente, criarComSucesso(chave).idTransacao());
        assertEquals(2, contarPorChave(chave));
    }

    @Test
    void transacaoForaDoPrazoVoltaComoExpirada() throws Exception {
        UUID chave = novaChave();
        // criacao é gravada em UTC (hibernate.jdbc.time_zone)
        UUID id = inserirDireto(CLIENT_ID, chave, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(16));

        PagamentoResponseDTO resposta = criarComSucesso(chave);

        assertEquals(id, resposta.idTransacao());
        assertEquals("EXPIRADO", resposta.status());
        assertEquals("PENDENTE", linha(id).get("status"));
    }

    @Test
    void semCredencialRetorna401() throws Exception {
        mockMvc.perform(post("/criar")
                .header("Idempotency-Key", novaChave())
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPO))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void pagarSemCredencialMarcaPagoEAvisa() throws Exception {
        UUID id = criarComSucesso(novaChave()).idTransacao();

        PagamentoResponseDTO resposta = pagarComSucesso(id);

        assertEquals("PAGO", resposta.status());
        assertEquals("PAGO", linha(id).get("status"));
        verify(webhook).avisar(CLIENT_ID, id, "PAGO");
    }

    @Test
    void pagarJaPagaRespondePagoSemReenviarWebhook() throws Exception {
        UUID id = criarComSucesso(novaChave()).idTransacao();

        pagarComSucesso(id);
        assertEquals("PAGO", pagarComSucesso(id).status());

        verify(webhook, times(1)).avisar(anyString(), any(), anyString());
    }

    @Test
    void webhookQueFalhaNaoDesfazOPagamento() throws Exception {
        UUID id = criarComSucesso(novaChave()).idTransacao();
        doThrow(new RuntimeException("interno fora do ar")).when(webhook).avisar(anyString(), any(), anyString());

        assertEquals("PAGO", pagarComSucesso(id).status());
        assertEquals("PAGO", linha(id).get("status"));
    }

    @Test
    void pagarExpiradaRetorna409SemWebhook() throws Exception {
        UUID chave = novaChave();
        UUID id = inserirDireto(CLIENT_ID, chave, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(16));

        pagar(id).andExpect(status().isConflict());

        assertEquals("PENDENTE", linha(id).get("status"));
        verify(webhook, never()).avisar(anyString(), any(), anyString());
    }

    @Test
    void pagarInexistenteRetorna404() throws Exception {
        pagar(UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void consultarDevolveOEstadoAtual() throws Exception {
        UUID id = criarComSucesso(novaChave()).idTransacao();
        assertEquals("PENDENTE", comSucesso(consultar(id)).status());

        pagarComSucesso(id);

        assertEquals("PAGO", comSucesso(consultar(id)).status());
    }

    @Test
    void consultarTransacaoDeOutroClienteRetorna404() throws Exception {
        UUID id = inserirDireto("outroCliente", novaChave(), LocalDateTime.now(ZoneOffset.UTC));

        consultar(id).andExpect(status().isNotFound());
    }

    @Test
    void consultarSemCredencialRetorna401() throws Exception {
        UUID id = criarComSucesso(novaChave()).idTransacao();

        mockMvc.perform(get("/consultar/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    void cancelarPendenteImpedeOPagamento() throws Exception {
        UUID id = criarComSucesso(novaChave()).idTransacao();

        assertEquals("CANCELADO", comSucesso(cancelar(id)).status());
        assertEquals("CANCELADO", comSucesso(cancelar(id)).status());

        pagar(id).andExpect(status().isConflict());
        assertEquals("CANCELADO", linha(id).get("status"));
        verify(webhook, never()).avisar(anyString(), any(), anyString());
    }

    @Test
    void cancelarPagaRetorna409EMantemPaga() throws Exception {
        UUID id = criarComSucesso(novaChave()).idTransacao();
        pagarComSucesso(id);

        cancelar(id).andExpect(status().isConflict());

        assertEquals("PAGO", linha(id).get("status"));
    }

    @Test
    void cancelarExpiradaDevolveExpiradaSemGravar() throws Exception {
        UUID id = inserirDireto(CLIENT_ID, novaChave(), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(16));

        assertEquals("EXPIRADO", comSucesso(cancelar(id)).status());
        assertEquals("PENDENTE", linha(id).get("status"));
    }

    @Nested
    @TestPropertySource(properties = "simulacao.resultado-pagar=RECUSADO")
    class ComPagamentoRecusado {

        @Test
        void pagarRetorna409MantemPendenteEAvisaRecusa() throws Exception {
            UUID id = criarComSucesso(novaChave()).idTransacao();

            pagar(id).andExpect(status().isConflict());

            assertEquals("PENDENTE", linha(id).get("status"));
            verify(webhook).avisar(CLIENT_ID, id, "RECUSADO");
        }

    }

    @Nested
    @TestPropertySource(properties = "simulacao.resultado-pagar=PAGO_SEM_AVISO")
    class ComPagoSemAviso {

        @Test
        void pagarRetorna500MasMarcaPagoSemWebhook() throws Exception {
            UUID id = criarComSucesso(novaChave()).idTransacao();

            pagar(id).andExpect(status().isInternalServerError());

            assertEquals("PAGO", linha(id).get("status"));
            verify(webhook, never()).avisar(anyString(), any(), anyString());
        }

    }

    // Desfecho do simulador é config do processo — outro valor, outro contexto Spring
    @Nested
    @TestPropertySource(properties = "simulacao.resultado=FALHA_ANTES_DE_GRAVAR")
    class ComFalhaAntesDeGravar {

        @Test
        void criarRetorna500SemGravar() throws Exception {
            UUID chave = novaChave();

            criar(chave).andExpect(status().isInternalServerError());

            assertEquals(0, contarPorChave(chave));
        }

    }

    @Nested
    @TestPropertySource(properties = "simulacao.resultado=FALHA_DEPOIS_DE_GRAVAR")
    class ComFalhaDepoisDeGravar {

        @Test
        void criarRetorna500MasGrava() throws Exception {
            UUID chave = novaChave();

            criar(chave).andExpect(status().isInternalServerError());

            assertEquals(1, contarPorChave(chave));
        }

    }

    private static final String CORPO = "{\"metodo\":\"cartao\",\"valor\":100.00}";

    private UUID novaChave() {
        UUID chave = UUID.randomUUID();
        chavesUsadas.add(chave);
        return chave;
    }

    private ResultActions criar(UUID chave) throws Exception {
        return mockMvc.perform(comoCliente(post("/criar"))
            .header("Idempotency-Key", chave)
            .contentType(MediaType.APPLICATION_JSON)
            .content(CORPO));
    }

    private PagamentoResponseDTO criarComSucesso(UUID chave) throws Exception {
        String corpo = criar(chave)
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(corpo, PagamentoResponseDTO.class);
    }

    private ResultActions pagar(UUID id) throws Exception {
        return mockMvc.perform(post("/pagar/" + id));
    }

    private PagamentoResponseDTO pagarComSucesso(UUID id) throws Exception {
        String corpo = pagar(id)
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(corpo, PagamentoResponseDTO.class);
    }

    private ResultActions consultar(UUID id) throws Exception {
        return mockMvc.perform(comoCliente(get("/consultar/" + id)));
    }

    private ResultActions cancelar(UUID id) throws Exception {
        return mockMvc.perform(comoCliente(post("/cancelar/" + id)));
    }

    private PagamentoResponseDTO comSucesso(ResultActions resultado) throws Exception {
        String corpo = resultado
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(corpo, PagamentoResponseDTO.class);
    }

    private static MockHttpServletRequestBuilder comoCliente(MockHttpServletRequestBuilder req) {
        return req
            .header("X-Client-Id", CLIENT_ID)
            .header("X-Client-Secret", CLIENT_SECRET);
    }

    private UUID inserirDireto(String idCliente, UUID chave, LocalDateTime criacao) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into pagamentos (id, id_cliente, chave_idempotencia, metodo, valor, criacao, status)
            values (?, ?, ?, 'cartao', 100.00, ?, 'PENDENTE')
            """, id, idCliente, chave, Timestamp.valueOf(criacao));
        return id;
    }

    private int contarPorChave(UUID chave) {
        return jdbc.queryForObject("select count(*) from pagamentos where chave_idempotencia = ?", Integer.class, chave);
    }

    private Map<String, Object> linha(UUID id) {
        return jdbc.queryForMap("select * from pagamentos where id = ?", id);
    }

}

package com.derso.arquitetura.pagamentointerno.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import com.derso.arquitetura.pagamentointerno.PagamentoExternoService;
import com.derso.arquitetura.pagamentointerno.PagamentoRepository;
import com.derso.arquitetura.pagamentointerno.WebhookService;
import com.derso.arquitetura.pagamentointerno.pendencias.PendenciasTask;
import com.derso.arquitetura.pagamentointerno.sessaocompra.SessaoCompraClient;
import com.derso.arquitetura.sagas.Messaging;
import com.derso.arquitetura.sagas.RabbitMQTestcontainersConfig;
import com.derso.arquitetura.webbase.teste.CredenciaisDev;
import com.derso.arquitetura.webbase.teste.PostgresTestcontainersConfig;

// sessaocompra e o broker mockados na fronteira — foco aqui é a linha de pagamentos e quem executa cada intenção.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "web", "test" })
@Import({ PostgresTestcontainersConfig.class, RabbitMQTestcontainersConfig.class })
class PagamentoWebhookTest {

    // pagamento-externo chama o webhook com o mesmo par que recebe em /criar
    private static final String CLIENT_ID = CredenciaisDev.de("PAGAMENTO_EXTERNO_ID");
    private static final String CLIENT_SECRET = CredenciaisDev.de("PAGAMENTO_EXTERNO_SECRET");
    private static final String FILA = "pagamento";

    private final List<UUID> sessoesUsadas = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PagamentoRepository repositorio;

    @Autowired
    private WebhookService servico;

    // mesmos mocks de PagamentoCriacaoTest: definição igual = contexto em cache compartilhado
    @MockitoBean
    private PagamentoExternoService externo;

    @MockitoBean
    private SessaoCompraClient sessaoCompra;

    @MockitoBean
    private Messaging sagas;

    @AfterEach
    void apagaPagamentosCriados() {
        sessoesUsadas.forEach(id -> jdbc.update("delete from pagamentos where id_sessao_compra = ?", id));
    }

    @Test
    void pagoAvisaSessaoPublicaComIdsInternosEConcluiAsIntencoes() throws Exception {
        Map<String, Object> pagamento = pagamentoAguardando();

        webhook(pagamento, "PAGO").andExpect(status().isOk());

        verify(sessaoCompra).pagamentoEfetuado((UUID) pagamento.get("id_sessao_compra"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> mensagem = ArgumentCaptor.forClass(Map.class);
        verify(sagas).publicar(eq(FILA), mensagem.capture());
        assertEquals(Messaging.EXECUTE, mensagem.getValue().get("tipo"));
        assertEquals(pagamento.get("id"), mensagem.getValue().get("idPagamento"));
        assertEquals(pagamento.get("id_sessao_compra"), mensagem.getValue().get("idSessaoCompra"));
        assertEquals(pagamento.get("id_reserva_hotel"), mensagem.getValue().get("idReservaHotel"));
        assertEquals(pagamento.get("id_reserva_voo_ida"), mensagem.getValue().get("idReservaVooIda"));
        assertEquals(pagamento.get("id_reserva_voo_volta"), mensagem.getValue().get("idReservaVooVolta"));
        assertNull(mensagem.getValue().get("idExterno"));

        Map<String, Object> linha = linha(pagamento);
        assertEquals("PAGO", linha.get("status"));
        assertEquals(false, linha.get("sessao_pendente"));
        assertEquals(false, linha.get("fila_pendente"));
        assertNull(linha.get("intencoes_ocupadas_em"));
    }

    @Test
    void pagoRepetidoEIgnorado() throws Exception {
        Map<String, Object> pagamento = pagamentoAguardando();

        webhook(pagamento, "PAGO").andExpect(status().isOk());
        webhook(pagamento, "PAGO").andExpect(status().isOk());

        verify(sessaoCompra, times(1)).pagamentoEfetuado(any());
        verify(sagas, times(1)).publicar(anyString(), any());
    }

    @Test
    void falhaNaSessaoNaoImpedeAFilaEOJobRetomaSoASessao() throws Exception {
        Map<String, Object> pagamento = pagamentoAguardando();
        doThrow(new RuntimeException("sessaocompra fora")).doNothing().when(sessaoCompra).pagamentoEfetuado(any());

        webhook(pagamento, "PAGO").andExpect(status().isOk());

        Map<String, Object> linha = linha(pagamento);
        assertEquals(true, linha.get("sessao_pendente"));
        assertEquals(false, linha.get("fila_pendente"));
        assertNull(linha.get("intencoes_ocupadas_em"));

        job().processaPendencias();

        verify(sessaoCompra, times(2)).pagamentoEfetuado(any());
        verify(sagas, times(1)).publicar(anyString(), any());
        assertEquals(false, linha(pagamento).get("sessao_pendente"));
    }

    @Test
    void falhaNaFilaMantemSoElaPendenteEOJobRepublica() throws Exception {
        Map<String, Object> pagamento = pagamentoAguardando();
        doThrow(new IOException("broker fora")).doNothing().when(sagas).publicar(anyString(), any());

        webhook(pagamento, "PAGO").andExpect(status().isOk());

        Map<String, Object> linha = linha(pagamento);
        assertEquals(false, linha.get("sessao_pendente"));
        assertEquals(true, linha.get("fila_pendente"));

        job().processaPendencias();

        verify(sessaoCompra, times(1)).pagamentoEfetuado(any());
        verify(sagas, times(2)).publicar(anyString(), any());
        assertEquals(false, linha(pagamento).get("fila_pendente"));
    }

    // Ocupada por outro (ex.: o webhook em andamento): o job só assume depois da janela, quando quem ocupou caiu
    @Test
    void jobNaoExecutaIntencaoOcupadaDentroDaJanela() throws Exception {
        Map<String, Object> pagamento = pagamentoAguardando();
        pagoComIntencoesOcupadasEm(pagamento, Instant.now());

        job().processaPendencias();
        verify(sessaoCompra, never()).pagamentoEfetuado(any());
        verify(sagas, never()).publicar(anyString(), any());

        pagoComIntencoesOcupadasEm(pagamento, Instant.now().minus(WebhookService.JANELA_INTENCOES).minusSeconds(1));
        job().processaPendencias();

        verify(sessaoCompra).pagamentoEfetuado((UUID) pagamento.get("id_sessao_compra"));
        verify(sagas).publicar(eq(FILA), any());
        Map<String, Object> linha = linha(pagamento);
        assertEquals(false, linha.get("sessao_pendente"));
        assertEquals(false, linha.get("fila_pendente"));
        assertNull(linha.get("intencoes_ocupadas_em"));
    }

    @Test
    void recusadoAvisaSessaoSemIntencaoEMantemAguardando() throws Exception {
        Map<String, Object> pagamento = pagamentoAguardando();

        webhook(pagamento, "RECUSADO").andExpect(status().isOk());

        verify(sessaoCompra).pagamentoRecusado((UUID) pagamento.get("id_sessao_compra"));
        verify(sagas, never()).publicar(anyString(), any());
        Map<String, Object> linha = linha(pagamento);
        assertEquals("AGUARDANDO_PAGAMENTO", linha.get("status"));
        assertEquals(false, linha.get("sessao_pendente"));
        assertEquals(false, linha.get("fila_pendente"));
    }

    // usuário tentou de novo na mesma URL e pagou
    @Test
    void pagoDepoisDeRecusadoSegueNormal() throws Exception {
        Map<String, Object> pagamento = pagamentoAguardando();

        webhook(pagamento, "RECUSADO").andExpect(status().isOk());
        webhook(pagamento, "PAGO").andExpect(status().isOk());

        verify(sessaoCompra).pagamentoEfetuado((UUID) pagamento.get("id_sessao_compra"));
        assertEquals("PAGO", linha(pagamento).get("status"));
    }

    @Test
    void recusadoDepoisDePagoEIgnorado() throws Exception {
        Map<String, Object> pagamento = pagamentoAguardando();

        webhook(pagamento, "PAGO").andExpect(status().isOk());
        webhook(pagamento, "RECUSADO").andExpect(status().isOk());

        verify(sessaoCompra, never()).pagamentoRecusado(any());
        assertEquals("PAGO", linha(pagamento).get("status"));
    }

    @Test
    void idTransacaoSemPagamentoFalhaAlto() throws Exception {
        mockMvc.perform(comCredencial(UUID.randomUUID(), "PAGO")).andExpect(status().is5xxServerError());

        verify(sessaoCompra, never()).pagamentoEfetuado(any());
        verify(sagas, never()).publicar(anyString(), any());
    }

    @Test
    void statusNaoTratadoFalhaAltoSemMexerNaLinha() throws Exception {
        Map<String, Object> pagamento = pagamentoAguardando();

        webhook(pagamento, "INESPERADO").andExpect(status().is5xxServerError());

        assertEquals("AGUARDANDO_PAGAMENTO", linha(pagamento).get("status"));
        verify(sessaoCompra, never()).pagamentoRecusado(any());
    }

    private PendenciasTask job() {
        return new PendenciasTask(repositorio, servico);
    }

    private Map<String, Object> pagamentoAguardando() {
        UUID id = UUID.randomUUID();
        UUID idSessao = UUID.randomUUID();
        UUID idExterno = UUID.randomUUID();
        sessoesUsadas.add(idSessao);
        jdbc.update("""
            insert into pagamentos (id, id_externo, id_sessao_compra, id_reserva_hotel, id_reserva_voo_ida,
                                    id_reserva_voo_volta, status, chave_idempotencia, url_pagamento, vencimento)
            values (?, ?, ?, ?, ?, ?, 'AGUARDANDO_PAGAMENTO', ?, ?, ?)
            """, id, idExterno, idSessao, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "http://gateway/pagar/" + idExterno, emUtc(Instant.now().plusSeconds(900)));
        return linha(id);
    }

    private void pagoComIntencoesOcupadasEm(Map<String, Object> pagamento, Instant ocupadaEm) {
        jdbc.update("""
            update pagamentos
            set status = 'PAGO', sessao_pendente = true, fila_pendente = true, intencoes_ocupadas_em = ?
            where id = ?
            """, emUtc(ocupadaEm), pagamento.get("id"));
    }

    // coluna sem fuso, gravada em UTC pelo Hibernate
    private static LocalDateTime emUtc(Instant instante) {
        return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private Map<String, Object> linha(Map<String, Object> pagamento) {
        return linha((UUID) pagamento.get("id"));
    }

    private Map<String, Object> linha(UUID id) {
        return jdbc.queryForMap("select * from pagamentos where id = ?", id);
    }

    private ResultActions webhook(Map<String, Object> pagamento, String status) throws Exception {
        return mockMvc.perform(comCredencial((UUID) pagamento.get("id_externo"), status));
    }

    private static MockHttpServletRequestBuilder comCredencial(UUID idTransacao, String status) {
        return post("/webhook")
            .header("X-Client-Id", CLIENT_ID)
            .header("X-Client-Secret", CLIENT_SECRET)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"idTransacao\":\"" + idTransacao + "\",\"status\":\"" + status + "\"}");
    }

}

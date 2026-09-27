package com.derso.arquitetura.pagamentoexterno.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.derso.arquitetura.pagamentoexterno.webhook.WebhookService;
import com.derso.arquitetura.webbase.config.BusinessException;

class PagamentoControllerTest {

    private final WebhookService webhook = mock(WebhookService.class);
    private final Authentication authentication = mock(Authentication.class);
    private final PagamentoRequestDTO dados = new PagamentoRequestDTO("cartao", new BigDecimal("100.00"));

    @Test
    void resultadoSucessoGeraTransacaoEDisparaWebhook() {
        when(authentication.getPrincipal()).thenReturn("clienteTeste");

        ResponseEntity<PagamentoResponseDTO> resposta =
            controller(ResultadoSimulado.SUCESSO).efetuarPagamento(dados, authentication);

        assertEquals(202, resposta.getStatusCode().value());
        assertNotNull(resposta.getBody().idTransacao());
        assertEquals("processando", resposta.getBody().status());
        verify(webhook).enviarResposta(eq("clienteTeste"), any());
    }

    @Test
    void resultadoFalhaNegocioLancaBusinessExceptionSemChamarWebhook() {
        assertThrows(BusinessException.class, () ->
            controller(ResultadoSimulado.FALHA_NEGOCIO).efetuarPagamento(dados, authentication)
        );

        verify(webhook, never()).enviarResposta(anyString(), any());
    }

    @Test
    void resultadoFalhaInfraLancaRuntimeExceptionSemChamarWebhook() {
        assertThrows(RuntimeException.class, () ->
            controller(ResultadoSimulado.FALHA_INFRA).efetuarPagamento(dados, authentication)
        );

        verify(webhook, never()).enviarResposta(anyString(), any());
    }

    private PagamentoController controller(ResultadoSimulado resultado) {
        return new PagamentoController(webhook, () -> resultado);
    }

}

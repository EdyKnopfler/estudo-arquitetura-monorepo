package com.derso.arquitetura.sagas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.MessageProperties;

class MessagingTest {

    private static final String FILA_ESTE_SERVICO = "hotel";
    private static final String FILA_ANTERIOR = "pagamento";
    private static final String FILA_PROXIMA = "voo";
    private static final long DELIVERY_TAG = 42L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Channel channel = mock(Channel.class);
    private final Messaging messaging;

    MessagingTest() throws IOException {
        messaging = new Messaging(objectMapper, channel);
    }

    @Test
    void ackParaFrenteConfirmaEPublicaNaProximaFilaComTipoExecute() throws Exception {
        capturarCallback(mensagem -> resultado(RetornoBroker.ACK, Encaminhamento.PARA_FRENTE)).andHandleWith();

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());

        Map<String, Object> publicada = capturarMensagemPublicada(FILA_PROXIMA);
        assertEquals(Messaging.EXECUTE, ((Number) publicada.get("tipo")).doubleValue());
    }

    @Test
    void ackParaTrasConfirmaEPublicaNaFilaAnteriorComTipoDesfaca() throws Exception {
        capturarCallback(mensagem -> resultado(RetornoBroker.ACK, Encaminhamento.PARA_TRAS)).andHandleWith();

        verify(channel).basicAck(DELIVERY_TAG, false);

        Map<String, Object> publicada = capturarMensagemPublicada(FILA_ANTERIOR);
        assertEquals(Messaging.DESFACA, ((Number) publicada.get("tipo")).doubleValue());
    }

    @Test
    void ackSemEncaminhamentoConfirmaSemPublicarNadaAckPuro() throws Exception {
        capturarCallback(mensagem -> resultado(RetornoBroker.ACK, Encaminhamento.NENHUM)).andHandleWith();

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicPublish(anyString(), anyString(), anyBoolean(), anyBoolean(), any(), any());
    }

    @Test
    void nackDlqDoRetornoDoHandlerNaoAckeia() throws Exception {
        capturarCallback(mensagem -> resultado(RetornoBroker.NACK_DLQ, Encaminhamento.NENHUM)).andHandleWith();

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void encaminhamentoParaFilaInexistenteFimDeCadeiaNaoPublicaNada() throws Exception {
        // filaProximoServico ausente (fim de cadeia) + PARA_FRENTE — nada pra publicar, sem erro.
        capturarCallback(Optional.empty(), Optional.of(FILA_ANTERIOR),
            mensagem -> resultado(RetornoBroker.ACK, Encaminhamento.PARA_FRENTE)).andHandleWith();

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicPublish(anyString(), anyString(), anyBoolean(), anyBoolean(), any(), any());
    }

    @Test
    void excecaoNaoCapturadaSempreNackDlqEPublicaDesfacaParaTrasPreservandoConteudo() throws Exception {
        capturarCallback(mensagem -> {
            throw new RuntimeException("bug inesperado");
        }).andHandleWith(Map.of("tipo", Messaging.EXECUTE, "rastreio", "abc-123"));

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());

        Map<String, Object> publicada = capturarMensagemPublicada(FILA_ANTERIOR);
        assertEquals(Messaging.DESFACA, ((Number) publicada.get("tipo")).doubleValue());
        assertEquals("abc-123", publicada.get("rastreio"));
    }

    @Test
    void excecaoNaoCapturadaSemFilaAnteriorApenasNackSemPublicar() throws Exception {
        capturarCallback(Optional.of(FILA_PROXIMA), Optional.empty(), mensagem -> {
            throw new RuntimeException("bug inesperado");
        }).andHandleWith();

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(channel, never()).basicPublish(anyString(), anyString(), anyBoolean(), anyBoolean(), any(), any());
    }

    // --- infraestrutura do teste: captura o DeliverCallback registrado em basicConsume e o invoca de verdade ---

    private static ResultadoHandler resultado(RetornoBroker broker, Encaminhamento encaminhamento) {
        return new ResultadoHandler(broker, encaminhamento);
    }

    private CallbackFixture capturarCallback(MessageHandler handler) throws IOException {
        return capturarCallback(Optional.of(FILA_PROXIMA), Optional.of(FILA_ANTERIOR), handler);
    }

    private CallbackFixture capturarCallback(Optional<String> proxima, Optional<String> anterior, MessageHandler handler) throws IOException {
        messaging.iniciarConsumo("consumidor-teste", FILA_ESTE_SERVICO, anterior, proxima, handler);

        ArgumentCaptor<DeliverCallback> captor = ArgumentCaptor.forClass(DeliverCallback.class);
        verify(channel, times(1)).basicConsume(
            eq(FILA_ESTE_SERVICO), eq(false), eq("consumidor-teste"), captor.capture(), any(CancelCallback.class)
        );

        return new CallbackFixture(captor.getValue());
    }

    private class CallbackFixture {
        private final DeliverCallback callback;

        CallbackFixture(DeliverCallback callback) {
            this.callback = callback;
        }

        void andHandleWith() throws IOException {
            andHandleWith(Map.of("tipo", Messaging.EXECUTE));
        }

        void andHandleWith(Map<String, Object> mensagem) throws IOException {
            byte[] body = objectMapper.writeValueAsBytes(mensagem);
            Envelope envelope = new Envelope(DELIVERY_TAG, false, Messaging.SAGAS_EXCHANGE, FILA_ESTE_SERVICO);
            Delivery delivery = new Delivery(envelope, MessageProperties.PERSISTENT_BASIC, body);

            try {
                callback.handle("consumidor-teste", delivery);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturarMensagemPublicada(String filaEsperada) throws IOException {
        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(channel).basicPublish(
            eq(Messaging.SAGAS_EXCHANGE), eq(filaEsperada), eq(true), eq(false),
            eq(MessageProperties.PERSISTENT_BASIC), bodyCaptor.capture()
        );

        return (Map<String, Object>) objectMapper.readValue(bodyCaptor.getValue(), Map.class);
    }
}

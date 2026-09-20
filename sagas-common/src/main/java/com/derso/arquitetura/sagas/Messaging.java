package com.derso.arquitetura.sagas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class Messaging {

    public static final String ERRORS_EXCHANGE = "errors_exchange";
    public static final String ERRORS_ROUTING_KEY = "errors";
    public static final String SAGAS_EXCHANGE = "sagas";

    public static final double EXECUTE = 1;
    public static final double DESFACA = 2;

    private final ObjectMapper objectMapper;
    private final Channel channel;
    private volatile String consumerTag;

    public Messaging(ObjectMapper objectMapper, Channel channel) throws IOException {
        this.objectMapper = objectMapper;
        this.channel = channel;
    }

    public void configurarServico(
            String filaEsteServico,
            Optional<String> filaServicoAnterior,
            Optional<String> filaProximoServico
    ) throws IOException {
        declararExchange(ERRORS_EXCHANGE);
        declararFila(ERRORS_ROUTING_KEY, ERRORS_EXCHANGE);
        configurarQos();
        declararExchange(SAGAS_EXCHANGE);

        if (filaServicoAnterior.isPresent()) {
            declararFila(filaServicoAnterior.get(), SAGAS_EXCHANGE);
        }

        declararFila(filaEsteServico, SAGAS_EXCHANGE);

        if (filaProximoServico.isPresent()) {
            declararFila(filaProximoServico.get(), SAGAS_EXCHANGE);
        }
    }

    public void iniciarConsumo(
            String nomeConsumidor,
            String filaEsteServico,
            Optional<String> filaServicoAnterior,
            Optional<String> filaProximoServico,
            MessageHandler handler
    ) throws IOException {
        DeliverCallback callback = (consumerTag, delivery) -> {

            Map<String, Object> mensagem = null;
            ResultadoHandler resultado = null;
            Exception erro = null;

            try {
                mensagem = decode(delivery.getBody());
                mensagem.putIfAbsent("tipo", EXECUTE);
                resultado = handler.handle(mensagem);
            } catch (Exception e) {
                erro = e;
            }

            long tag = delivery.getEnvelope().getDeliveryTag();

            // erro não capturado pelo handler (bug/falha sistêmica) — sempre nack+dlq+compensação, não passa pelo ResultadoHandler
            if (erro != null) {
                channel.basicNack(tag, false, false);

                if (mensagem != null && filaServicoAnterior.isPresent()) {
                    mensagem.put("tipo", DESFACA);
                    publicar(filaServicoAnterior.get(), mensagem);
                }
                return;
            }

            Objects.requireNonNull(resultado, "handler deve retornar um ResultadoHandler quando não lança exceção");

            switch (resultado.retornoBroker()) {
                case ACK -> channel.basicAck(tag, false);
                case NACK_DLQ -> channel.basicNack(tag, false, false);
            }

            Optional<String> filaDestino = switch (resultado.encaminhamento()) {
                case PARA_FRENTE -> filaProximoServico;
                case PARA_TRAS -> filaServicoAnterior;
                case NENHUM -> Optional.empty();
            };

            if (filaDestino.isPresent()) {
                mensagem.put("tipo", resultado.encaminhamento() == Encaminhamento.PARA_FRENTE ? EXECUTE : DESFACA);
                publicar(filaDestino.get(), mensagem);
            }
        };

        consumerTag = channel.basicConsume(
                filaEsteServico,
                false,
                nomeConsumidor,
                callback,
                consumerTag -> {
                    System.out.println(consumerTag + " sendo cancelado pelo broker");
                }
        );
    }

    public void pararConsumo() {
        try {
            if (consumerTag != null && channel.isOpen()) {
                channel.basicCancel(consumerTag);
            }
        } catch (IOException e) {
            System.err.println("ERRO AO FECHAR CANAL:");
            e.printStackTrace();
        }
    }

    public void publicar(String fila, Object mensagem) throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(mensagem);

        channel.basicPublish(
                SAGAS_EXCHANGE,
                fila,
                true,
                false,
                MessageProperties.PERSISTENT_BASIC,
                body
        );
    }

    private void declararExchange(String nome) throws IOException {
        channel.exchangeDeclare(
                nome,
                BuiltinExchangeType.DIRECT,
                true,
                false,
                false,
                null
        );
    }

    private void declararFila(String fila, String exchange) throws IOException {
        Map<String, Object> args = Map.of(
                "x-dead-letter-exchange", ERRORS_EXCHANGE,
                "x-dead-letter-routing-key", ERRORS_ROUTING_KEY
        );

        channel.queueDeclare(fila, true, false, false, args);
        channel.queueBind(fila, exchange, fila);
    }

    private void configurarQos() throws IOException {
        channel.basicQos(1);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decode(byte[] body) throws IOException {
        Object raw = objectMapper.readValue(body, Object.class);

        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException("JSON inválido");
        }

        return (Map<String, Object>) raw;
    }
}


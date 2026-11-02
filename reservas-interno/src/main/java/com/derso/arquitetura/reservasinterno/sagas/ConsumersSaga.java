package com.derso.arquitetura.reservasinterno.sagas;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

// sagas.consumers: um processo pode ser vários nós da cadeia do mesmo domínio (voo-ida e voo-volta, mesmo banco).
// campoid: qual id da mensagem é a reserva deste nó.
@ConfigurationProperties("sagas")
public record ConsumersSaga(List<Consumer> consumers) {

    public record Consumer(String estafila, String filaanterior, String proximafila, String campoid) {
    }

}

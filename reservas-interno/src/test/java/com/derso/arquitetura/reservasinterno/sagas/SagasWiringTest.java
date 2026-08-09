package com.derso.arquitetura.reservasinterno.sagas;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.derso.arquitetura.sagas.RabbitMQTestcontainersConfig;
import com.rabbitmq.client.Channel;

// Dummy: só prova que SagasWiring conecta de verdade no RabbitMQ (compose ou Testcontainers).
// Preenche com lógica de negócio quando os handlers deixarem de ser stub.
@SpringBootTest
@ActiveProfiles({ "sagas", "hotel" })
@Import(RabbitMQTestcontainersConfig.class)
class SagasWiringTest {

    @Autowired
    private Channel channel;

    @Test
    void conectaNoRabbitMQ() {
        assertTrue(channel.isOpen());
    }

}

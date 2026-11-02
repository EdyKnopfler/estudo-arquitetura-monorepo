package com.derso.arquitetura.sessaocompra.sagas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.derso.arquitetura.sagas.RabbitMQTestcontainersConfig;
import com.derso.arquitetura.webbase.teste.PostgresTestcontainersConfig;
import com.rabbitmq.client.Channel;

// O papel sagas sobe consumindo a fila da ponta final da cadeia
@SpringBootTest
@ActiveProfiles({ "sagas", "test" })
@Import({ PostgresTestcontainersConfig.class, RabbitMQTestcontainersConfig.class })
class SagasWiringTest {

    @Autowired
    private Channel channel;

    @Test
    void consomeAFilaSessaocompra() throws Exception {
        assertEquals(1, channel.queueDeclarePassive("sessaocompra").getConsumerCount());
    }

}

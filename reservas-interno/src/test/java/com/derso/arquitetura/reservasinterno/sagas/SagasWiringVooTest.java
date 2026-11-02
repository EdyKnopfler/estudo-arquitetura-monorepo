package com.derso.arquitetura.reservasinterno.sagas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.derso.arquitetura.sagas.RabbitMQTestcontainersConfig;
import com.derso.arquitetura.webbase.teste.PostgresTestcontainersConfig;
import com.rabbitmq.client.Channel;

// Um processo, dois nós da cadeia: cada perna do voo com consumer próprio na sua fila.
@SpringBootTest
@ActiveProfiles({ "sagas", "voo", "test" })
@Import({ PostgresTestcontainersConfig.class, RabbitMQTestcontainersConfig.class })
class SagasWiringVooTest {

    @Autowired
    private Channel channel;

    @Test
    void consomeAsFilasDasDuasPernas() throws Exception {
        assertEquals(1, channel.queueDeclarePassive("voo-ida").getConsumerCount());
        assertEquals(1, channel.queueDeclarePassive("voo-volta").getConsumerCount());
    }

}

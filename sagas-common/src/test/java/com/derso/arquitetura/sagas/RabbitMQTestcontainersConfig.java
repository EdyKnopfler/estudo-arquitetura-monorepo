package com.derso.arquitetura.sagas;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.rabbitmq.RabbitMQContainer;

// Estratégia de testes: docs/testing-strategy.md
// RabbitConfig usa o client cru do RabbitMQ, não Spring AMQP — sem @ServiceConnection
// pra plugar, por isso injeta host/porta via DynamicPropertyRegistrar.
@TestConfiguration(proxyBeanMethods = false)
public class RabbitMQTestcontainersConfig {

    // destroyMethod vazio: senão o Spring para o container no fim do contexto e anula o reuse
    @Bean(destroyMethod = "")
    RabbitMQContainer rabbitMQContainer() {
        return new RabbitMQContainer("rabbitmq:4.2.2-management-alpine").withReuse(true);
    }

    @Bean
    DynamicPropertyRegistrar rabbitMQProperties(RabbitMQContainer rabbitMQContainer) {
        return registry -> {
            registry.add("sagas.rabbithost", rabbitMQContainer::getHost);
            registry.add("sagas.rabbitport", rabbitMQContainer::getAmqpPort);
            registry.add("sagas.rabbituser", rabbitMQContainer::getAdminUsername);
            registry.add("sagas.rabbitpassword", rabbitMQContainer::getAdminPassword);
        };
    }

}

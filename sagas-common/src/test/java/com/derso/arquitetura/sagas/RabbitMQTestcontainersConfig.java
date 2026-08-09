package com.derso.arquitetura.sagas;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.rabbitmq.RabbitMQContainer;

// Testcontainers vs. serviços já no ar: docs/testing-strategy.md
// RabbitConfig usa o client cru do RabbitMQ, não Spring AMQP — sem @ServiceConnection
// pra plugar, por isso injeta host/porta via DynamicPropertyRegistrar.
@TestConfiguration(proxyBeanMethods = false)
public class RabbitMQTestcontainersConfig {

    @Bean
    @ConditionalOnProperty(name = "sagas.testcontainers", havingValue = "true", matchIfMissing = false)
    RabbitMQContainer rabbitMQContainer() {
        return new RabbitMQContainer("rabbitmq:4.2.2-management-alpine");
    }

    @Bean
    @ConditionalOnProperty(name = "sagas.testcontainers", havingValue = "true", matchIfMissing = false)
    DynamicPropertyRegistrar rabbitMQProperties(RabbitMQContainer rabbitMQContainer) {
        return registry -> {
            registry.add("sagas.rabbithost", rabbitMQContainer::getHost);
            registry.add("sagas.rabbitport", rabbitMQContainer::getAmqpPort);
            registry.add("sagas.rabbituser", rabbitMQContainer::getAdminUsername);
            registry.add("sagas.rabbitpassword", rabbitMQContainer::getAdminPassword);
        };
    }

}

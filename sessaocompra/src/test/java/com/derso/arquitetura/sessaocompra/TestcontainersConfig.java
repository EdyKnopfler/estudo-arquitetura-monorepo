package com.derso.arquitetura.sessaocompra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Testcontainers vs. serviços já no ar: docs/testing-strategy.md
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfig {

    @Bean
    @ServiceConnection
    @ConditionalOnProperty(name = "sagas.testcontainers", havingValue = "true", matchIfMissing = false)
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:18.1");
    }

}

package com.derso.arquitetura.webbase.teste;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Um Postgres (reuse) pra todos os módulos, um database por módulo — espelha o compose.
// Estratégia de testes: docs/testing-strategy.md
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfig {

    // destroyMethod vazio: senão o Spring para o container no fim do contexto e anula o reuse
    @Bean(destroyMethod = "")
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:18.1").withReuse(true);
    }

    // sem @ServiceConnection: ele apontaria pro database default do container, compartilhado
    @Bean
    DynamicPropertyRegistrar postgresProperties(PostgreSQLContainer postgres, Environment env) {
        String database = env.getRequiredProperty("spring.application.name").replace('-', '_');

        return registry -> {
            registry.add("spring.datasource.url", () -> urlDoDatabase(postgres, database));
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        };
    }

    private static String urlDoDatabase(PostgreSQLContainer postgres, String database) {
        if (!postgres.isRunning()) {
            postgres.start();
        }
        try (Connection conexao = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            boolean existe = conexao.createStatement()
                .executeQuery("select 1 from pg_database where datname = '" + database + "'")
                .next();
            if (!existe) {
                conexao.createStatement().execute("create database " + database);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("não criou o database de teste " + database, e);
        }
        return postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + database);
    }

}

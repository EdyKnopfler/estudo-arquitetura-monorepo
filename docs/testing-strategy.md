# Estratégia de testes

Testes de integração que dependem de Postgres ou RabbitMQ rodam, por padrão, contra os serviços do `docker-compose up` já no ar — sem overhead de subir container a cada execução pela IDE. O `application.yaml` de cada módulo já aponta pro Postgres/RabbitMQ do compose, sem config extra.

Config de RabbitMQ via Testcontainers vive em `sagas-common` (`RabbitMQTestcontainersConfig`, empacotada como test-jar) — é infra de teste da SAGA, todo módulo que participa da coreografia importa de lá em vez de duplicar. RabbitMQ usa o client cru (`RabbitConfig`, sem Spring AMQP), então não tem `@ServiceConnection` pronto — a porta/host do container entra via `DynamicPropertyRegistrar`. Cada módulo consumidor ainda precisa declarar `spring-boot-testcontainers`/`testcontainers-rabbitmq` no próprio pom (escopo `test` não é transitivo via test-jar).

Pra rodar self-contained (CI/CD, ou dev sem o compose no ar), liga o Testcontainers explicitamente: `SAGAS_TESTCONTAINERS=true mvn test`. O bean de conexão é condicional (`@ConditionalOnProperty`, opt-in).

- dev: ciclo rápido, sem subir/derrubar banco a cada rodada
- CI/CD: ativa a flag, suíte controla o container sozinha

Trade-off aceito no modo padrão: o teste não é isolado — reaproveita o banco de dev como está, então pode ver dado de execuções anteriores.

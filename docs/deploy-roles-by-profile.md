# Como escolher o papel (web vs. sagas) por profile

Mecanismo de implementação do refactor descrito em [module-boundaries.md](module-boundaries.md#3-artefato-único-com-dois-entrypoints-escaláveis-por-configuração--concluído-para-reservas): unificar `-common`/`-web`/`-sagas` de cada domínio num artefato só, escolhendo em runtime se a instância atende REST, fila, ou ambos. Já aplicado em `reservas-interno`; pendente em `pagamento-interno`.

## Profiles se combinam

`SPRING_PROFILES_ACTIVE` aceita lista separada por vírgula. Hoje o projeto já usa profile pra domínio (`hotel`/`voo`); o refactor adiciona um segundo eixo ortogonal, papel (`web`/`sagas`), ativado junto:

```
SPRING_PROFILES_ACTIVE=hotel,web    # instância REST de hotel
SPRING_PROFILES_ACTIVE=hotel,sagas  # instância consumidora de fila de hotel
SPRING_PROFILES_ACTIVE=voo,web
SPRING_PROFILES_ACTIVE=voo,sagas
```

Arquivo novo por papel: `application-web.yaml` / `application-sagas.yaml`, ao lado dos `application-hotel.yaml` / `application-voo.yaml` que já existem.

## `@Profile` controla quais beans existem

`@Profile("web")` num `@RestController`/`@Service`, `@Profile("sagas")` num listener/`@Component` — se o profile não está ativo, o bean **não é instanciado** (não é "existe desligado").

```java
@RestController
@Profile("web")
public class ReservasInternoController { ... }

@Component
@Profile("sagas")
public class ReservasSagas implements SmartLifecycle { ... }
```

## Pegadinha: propagar o profile pra cadeia de configuração inteira

`@Profile` no componente "visível" não basta se ele depende de `@Configuration`/`@Bean` com efeito colateral na subida (abrir socket, criar pool). Exemplo real deste projeto: `RabbitConfig` (`sagas-common`) declara `Connection`/`Channel` sem `@Profile` — numa instância `web`-only isso abriria conexão com RabbitMQ à toa. Precisa do mesmo `@Profile("sagas")`:

```java
@Configuration
@Profile("sagas")
public class RabbitConfig { ... }
```

## Desligar o servidor web na instância só-fila

Property, não anotação — em `application-sagas.yaml`:

```yaml
spring:
  main:
    web-application-type: none
```

## Opcional: nomear a combinação (profile groups)

```yaml
# application.yaml
spring:
  profiles:
    group:
      reservas-hotel-web: hotel,web
      reservas-hotel-sagas: hotel,sagas
```

Deixa o `docker-compose.yml` mais legível (`SPRING_PROFILES_ACTIVE: reservas-hotel-web`), sem mudar a mecânica.

## Checklist para o refactor

**Aplicado em `reservas-interno`** — usar de roteiro de novo quando `pagamento-interno-sagas` for criado e `pagamento-interno-{common,web}` passarem pelo mesmo tratamento.

- [x] Todo `@RestController`/`@Service` hoje em `-web` ganha `@Profile("web")`.
- [x] Todo listener/`SmartLifecycle` hoje em `-sagas` ganha `@Profile("sagas")`, **junto com** a cadeia de `@Configuration` da qual depende (conexão/canal RabbitMQ) — em vez de anotar `RabbitConfig` direto (o que vazaria a convenção de profile pra dentro da lib `sagas-common`), a gate ficou numa classe-ponte só do lado do consumidor: `@Configuration @Profile("sagas") @Import({SagasJacksonConfig.class, RabbitConfig.class, SagasMessaging.class})`. Funciona porque `@Profile` é avaliado antes do `@Import` ser processado.
- [x] `application-sagas.yaml` novo com `spring.main.web-application-type: none`.
- [x] Decidir se `application-web.yaml` chega a ser necessário (só se houver algo específico do papel web além do que já está em `application.yaml`/`application-<domínio>.yaml`) — não foi necessário; porta é domínio-específica e ficou nos arquivos `application-hotel.yaml`/`application-voo.yaml`.
- [x] Confirmar que rodar Flyway nos dois papéis é aceitável (schema precisa existir de qualquer forma; Flyway tem lock próprio contra corrida entre instâncias migrando ao mesmo tempo).

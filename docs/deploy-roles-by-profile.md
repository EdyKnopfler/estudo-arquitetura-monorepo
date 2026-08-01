# Como escolher o papel (web vs. sagas) por profile

Mecanismo de implementação para o refactor planejado em [module-boundaries.md](module-boundaries.md#3-artefato-único-com-dois-entrypoints-escaláveis-por-configuração--direção-escolhida-para-o-próximo-refactor): unificar `-common`/`-web`/`-sagas` de cada domínio num artefato só, escolhendo em runtime se a instância atende REST, fila, ou ambos.

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

- [ ] Todo `@RestController`/`@Service` hoje em `-web` ganha `@Profile("web")`.
- [ ] Todo listener/`SmartLifecycle` hoje em `-sagas` ganha `@Profile("sagas")`, **junto com** a cadeia de `@Configuration` da qual depende (conexão/canal RabbitMQ).
- [ ] `application-sagas.yaml` novo com `spring.main.web-application-type: none`.
- [ ] Decidir se `application-web.yaml` chega a ser necessário (só se houver algo específico do papel web além do que já está em `application.yaml`/`application-<domínio>.yaml`).
- [ ] Confirmar que rodar Flyway nos dois papéis é aceitável (schema precisa existir de qualquer forma; Flyway tem lock próprio contra corrida entre instâncias migrando ao mesmo tempo).

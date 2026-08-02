# Como escolher o papel (web vs. sagas) por profile

Mecanismo de implementação do refactor descrito em [module-boundaries.md](module-boundaries.md#3-artefato-único-com-dois-entrypoints-escaláveis-por-configuração--concluído-para-reservas-pagamento-e-sessão-de-compra): unificar `-common`/`-web`/`-sagas` de cada domínio num artefato só, escolhendo em runtime se a instância atende REST, fila, ou ambos. Já aplicado em `reservas-interno` e `pagamento-interno`.

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

`@Profile` no componente "visível" não basta se ele depende de `@Configuration`/`@Bean` com efeito colateral na subida (abrir socket, criar pool). Exemplo real deste projeto: `RabbitConfig` (`sagas-common`) declara `Connection`/`Channel` sem `@Profile` — numa instância `web`-only isso abriria conexão com RabbitMQ à toa.

**Decisão tomada:** não anotar `RabbitConfig` diretamente — isso vazaria a convenção de nome de profile (`"sagas"`) pra dentro de uma biblioteca (`sagas-common`) que deveria ser agnóstica a quem a consome. Em vez disso, cada módulo consumidor tem sua própria classe-ponte, só ela carrega o `@Profile`, e usa `@Import` pra trazer as classes da lib:

```java
// com.derso.arquitetura.reservasinterno.sagas.SagasWiring
// (mesma estrutura em com.derso.arquitetura.pagamentointerno.sagas.SagasWiring)
@Configuration
@Profile("sagas")
@Import({ SagasJacksonConfig.class, RabbitConfig.class, SagasMessaging.class })
public class SagasWiring {}
```

Funciona porque `@Profile` é um `@Conditional` avaliado pelo `ConfigurationClassParser` **antes** de processar `@Import` — se o profile não bate, a classe inteira (e tudo que ela importaria) é ignorada. `@Import` aceita `@Component` puro além de `@Configuration`, por isso dá pra importar `SagasMessaging` (que é `@Component`) junto com os dois `@Configuration`. Consequência prática: `com.derso.arquitetura.sagas` **não entra** no `@ComponentScan` do app (senão seria varrido incondicionalmente, contornando a gate) — a única porta de entrada pro pacote da lib passa a ser esse `@Import` explícito.

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

**Aplicado em `reservas-interno` e `pagamento-interno`.** Também aplicado em `sessaocompra`, com uma variação: como esse domínio não participa da coreografia SAGA, o segundo papel se chama `timeout` (não `sagas`) e não tem `SagasWiring`/dependência de `sagas-common` — é só `@Profile("timeout")` no `TimeoutTask` (`@Scheduled`) + `application-timeout.yaml` com `web-application-type: none`. Ver [module-boundaries.md](module-boundaries.md#3-artefato-único-com-dois-entrypoints-escaláveis-por-configuração--concluído-para-reservas-pagamento-e-sessão-de-compra).

- [x] Todo `@RestController`/`@Service` hoje em `-web` ganha `@Profile("web")`.
- [x] Todo listener/`SmartLifecycle` hoje em `-sagas` ganha `@Profile("sagas")`, **junto com** a cadeia de `@Configuration` da qual depende (conexão/canal RabbitMQ) — via a classe-ponte `SagasWiring` (ver seção acima), não anotando `sagas-common` diretamente.
- [x] `application-sagas.yaml` novo com `spring.main.web-application-type: none`.
- [x] Decidir se `application-web.yaml` chega a ser necessário (só se houver algo específico do papel web além do que já está em `application.yaml`/`application-<domínio>.yaml`) — não foi necessário em nenhum dos dois domínios.
- [x] **Desligar Flyway em todo profile que não seja `web`** (`spring.flyway.enabled: false` em `application-sagas.yaml`/`application-timeout.yaml`). Testamos rodar Flyway nos dois papéis primeiro — funcionava, mas era regressão: antes do refactor, só `-web` tinha Flyway como dependência. Achamos o problema de fato em `sessaocompra-timeout` (pool pequeno herdado do módulo antigo, Flyway precisa de 2 conexões simultâneas e travava contra si mesmo); em `reservas-interno`/`pagamento-interno` não travou só porque o pool nunca foi reduzido pro papel `sagas` — o risco existia igual, mascarado. Ver [todo.md](todo.md).

Pagamento não tem eixo de domínio (hotel/voo) — só o eixo de papel, então `SPRING_PROFILES_ACTIVE=web` / `SPRING_PROFILES_ACTIVE=sagas` direto, sem combinar com nada. A fiação da fila (`sagas.estafila`/`proximafila`, sem `filaanterior`) fica em `application-sagas.yaml` mesmo, não precisou de arquivo por domínio.

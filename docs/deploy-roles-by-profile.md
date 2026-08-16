# Como escolher o papel (web vs. sagas) por profile

Cada domínio (`reservas-interno`, `pagamento-interno`, `sessaocompra`) é um artefato único com controller REST e listener de fila no mesmo processo; qual entrypoint fica ativo em cada instância é decidido em runtime por profile Spring.

## Profiles se combinam

`SPRING_PROFILES_ACTIVE` aceita lista separada por vírgula. O projeto usa profile pra domínio (`hotel`/`voo`) e, ortogonalmente, pra papel (`web`/`sagas`), ativados juntos:

```
SPRING_PROFILES_ACTIVE=hotel,web    # instância REST de hotel
SPRING_PROFILES_ACTIVE=hotel,sagas  # instância consumidora de fila de hotel
SPRING_PROFILES_ACTIVE=voo,web
SPRING_PROFILES_ACTIVE=voo,sagas
```

Arquivo por papel: `application-web.yaml` / `application-sagas.yaml`, ao lado dos `application-hotel.yaml` / `application-voo.yaml` de domínio.

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

## Aplicado em cada domínio

`reservas-interno` e `pagamento-interno`: todo `@RestController`/`@Service` do papel REST leva `@Profile("web")`; todo listener/`SmartLifecycle` do papel fila leva `@Profile("sagas")`, junto com a cadeia de `@Configuration` da qual depende — via a classe-ponte `SagasWiring` (ver seção acima). `application-sagas.yaml` seta `spring.main.web-application-type: none`; não foi necessário um `application-web.yaml` em nenhum dos dois domínios (nada específico do papel web além do que já está em `application.yaml`/`application-<domínio>.yaml`).

`sessaocompra` segue o mesmo mecanismo com uma variação: como esse domínio não participa da coreografia SAGA, o segundo papel se chama `timeout` (não `sagas`) e não tem `SagasWiring`/dependência de `sagas-common` — é só `@Profile("timeout")` no `TimeoutTask` (`@Scheduled`) + `application-timeout.yaml` com `web-application-type: none`. (Desenho ainda não implementado adiciona um papel de fila a `sessaocompra` — ver [purchase-flow-design.md](purchase-flow-design.md).)

**Flyway roda só no profile `web`**: `spring.flyway.enabled: false` explícito em `application-sagas.yaml`/`application-timeout.yaml` dos três domínios. Non-óbvio: Flyway precisa de 2 conexões simultâneas pra coordenação de lock durante a migration — com múltiplas instâncias de um profile não-web tentando migrar ao mesmo tempo (e pool pequeno, como em `sessaocompra-timeout`), elas travam entre si até estourar timeout.

Pagamento não tem eixo de domínio (hotel/voo) — só o eixo de papel, então `SPRING_PROFILES_ACTIVE=web` / `SPRING_PROFILES_ACTIVE=sagas` direto, sem combinar com nada. A fiação da fila (`sagas.estafila`/`proximafila`, sem `filaanterior`) fica em `application-sagas.yaml` mesmo, não precisou de arquivo por domínio.

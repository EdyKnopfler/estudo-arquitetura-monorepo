# Estratégia de testes

Dois tipos de teste, cada um com o modo que ele exige — nenhum teste roda em dois modos.

## Rodar

- tudo: `./mvnw install && ./mvnw test -Pintegrado`
  - o reactor roda um módulo por vez, em ordem de dependência, e para na primeira falha
  - `install` antes: gera os jars que o integrado sobe (ver [abaixo](#teste-integrado-mvnw-package--dskiptests--mvnw-test--pintegrado))
  - nunca os dois em paralelo: o integrado precisa dos jars, e a RAM não aguenta
- um módulo: `./mvnw -pl <módulo> install`, depois `./mvnw -pl <módulo> test -Pintegrado`
  - `web-base` e `sagas-common` precisam estar instalados (test-jars)
- no fim, liberar RAM: comando em [Reuse de containers](#reuse-de-containers)

## Teste de microsserviço (`./mvnw test`)

Um serviço isolado, rodando no próprio processo do teste.

- padrão: `@SpringBootTest` + MockMvc — o serviço inteiro (segurança, validação, JPA, Flyway) de verdade
- Postgres e RabbitMQ: Testcontainers, sempre
  - Postgres: `PostgresTestcontainersConfig` do test-jar do `web-base`
  - RabbitMQ: `RabbitMQTestcontainersConfig` do test-jar do `sagas-common`
    - `RabbitConfig` usa o client cru (sem Spring AMQP), sem `@ServiceConnection` — host/porta entram via `DynamicPropertyRegistrar`
    - módulo consumidor declara `spring-boot-testcontainers`/`testcontainers-rabbitmq` no próprio pom (escopo `test` não é transitivo via test-jar)
- outro serviço chamado por HTTP: mockado na fronteira (`@MockitoBean` no client/service que faz a chamada)
- confere efeito, não só resposta: lê o banco com `JdbcTemplate` (SQL cru — pega coluna trocada que o próprio `@Entity` esconderia)

### Reuse de containers

Postgres/RabbitMQ ficam de pé entre execuções (`withReuse(true)`) — rodar uma classe ou método pela IDE conecta em ~1s.

- ligado pelo projeto, não por máquina: env `TESTCONTAINERS_REUSE_ENABLE` no surefire (parent pom) e em `java.test.config` do `.vscode/settings.json`
  - Testcontainers só lê essa chave de env ou `~/.testcontainers.properties` — arquivo no classpath é ignorado de propósito
  - CI: `-Dtestes.reuse=false` volta ao descartável
- `@Bean(destroyMethod = "")` nas configs: com o destroy method inferido (`close`), o Spring para o container no fim do contexto e anula o reuse
- estado persiste entre execuções — por isso a regra de limpeza abaixo é obrigatória
- derrubar (liberar RAM, ou depois de editar migration já aplicada): `docker rm -f $(docker ps -aq --filter label=org.testcontainers.hash)`

## Teste integrado (`./mvnw package -DskipTests && ./mvnw test -Pintegrado`)

Serviços reais conversando entre si.

- marcado com `@Tag("integrado")` — fora do `mvn test` padrão (parent pom exclui a tag; profile `integrado` inverte e roda só eles)
- cada serviço sobe como container via `ServicoEmContainer` (test-jar do `web-base`): jar já compilado no host + imagem só com o JRE, numa network do Testcontainers
  - por que não o `Dockerfile` do módulo: ele builda com Maven dentro do Docker usando `RUN --mount` (cache), que exige BuildKit — o cliente Docker do Testcontainers não suporta. E build Maven dentro do Docker é justamente o passo que pesa na RAM
  - consequência: o jar precisa existir antes (`package`); sem ele o teste falha dizendo isso. Jar velho = teste contra código velho
- sem reuse: cada classe monta e derruba seus containers

## Regras (valem pros dois tipos)

- Docker precisa estar no ar; o teste sobe tudo o mais sozinho — ninguém (nem agente) sobe ambiente à mão pra teste
- sem `contextLoads` — qualquer `@SpringBootTest` do módulo já prova que o contexto sobe
- dado único por teste (UUID, e-mail, CPF aleatórios) e o teste apaga o que criou em `@AfterEach`
  - container é compartilhado entre testes e, com reuse, entre execuções — um teste não pode depender do estado deixado por outro
  - nunca `DELETE`/`TRUNCATE` geral
- sem `@Transactional` na classe de teste: o service entraria na transação do teste, sem commit real, e a leitura via JDBC não veria a linha
- memória: rodar módulo a módulo (`-pl`) numa máquina de 8 GB

## Simulação de falha dos `-externo`

Os simuladores sorteiam falha de propósito (`CHANCE_FALHA`). Teste precisa de desfecho determinístico:

- só com profile `test` ativo no simulador (`SimuladorDeTeste`); fora dele, sempre sorteio
- desfecho só por config do processo: propriedade `simulacao.resultado` (env `SIMULACAO_RESULTADO`)
  - no `pagamento-externo`, o pagar tem a sua (`simulacao.resultado-pagar`): criar e pagar combinam desfechos diferentes no mesmo processo
  - teste de microsserviço: `@TestPropertySource` por classe; outro desfecho vai num `@Nested` com o seu (outro contexto Spring)
  - teste integrado: um container por desfecho, um de cada vez
- sem header por requisição: ele só existia pro modo contra o compose já no ar (descartado, ver abaixo)

## Descartado: rodar testes contra instâncias do compose já no ar

Tentado em 2026-09-26: modo padrão contra o `docker compose` de dev (sem subir container por execução) + Testcontainers opt-in pra CI. Descartado porque:

- todo teste tinha que funcionar nos dois ambientes — configuração dupla pra tudo:
  - databases `*_test` criados à mão (o `databases.sql` só roda na criação do volume)
  - URL de teste em documento `on-profile: test` de 7 YAMLs, com armadilha de precedência entre arquivos de profile
  - vhost próprio no RabbitMQ, pra não cair nas filas dos consumidores `-sagas` de dev
  - instâncias `-test` dos simuladores no compose (as de dev gravam no banco de dev)
- estado persiste entre execuções — limpeza vira requisito crítico em vez de boa prática
- ambiente pesado: JVMs extras + build de imagens esgotaram RAM e swap da máquina de dev
- ganho pequeno: Postgres/RabbitMQ em container sobem em segundos, e o reuse cobre o ciclo rápido de dev sem modo duplo

`docker compose up` segue sendo o jeito de **rodar a aplicação** localmente — só não é ambiente de teste.

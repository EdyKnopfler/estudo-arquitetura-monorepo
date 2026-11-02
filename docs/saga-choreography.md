# Coreografia SAGA

Implementação própria sobre o cliente Java cru do RabbitMQ (`com.rabbitmq.client`, não Spring AMQP), centralizada em `sagas-common`. É **coreografia**, não orquestração: cada serviço só conhece a fila anterior e a próxima na cadeia, sem um coordenador central.

## Peças

- `RabbitConfig` (`sagas-common`): abre uma única `Connection`/`Channel` por instância a partir de `sagas.rabbithost/port/user/password`.
  - `Channel` não aceita publicação concorrente: `Messaging.publicar` é `synchronized` — publisher confirms e channels por thread em [todo.md](todo.md#hygiene--housekeeping)
- `Messaging` (`sagas-common`): declara exchanges/filas e implementa o protocolo de consumo/publicação.
- `MessageHandler`: interface funcional que cada serviço implementa com a lógica de negócio real (`ResultadoHandler handle(Map<String, Object> mensagem)`).
- Cada papel `sagas` (ex.: `reservas-interno/.../sagas/ReservasSagas.java`, `pagamento-interno/.../sagas/PagamentoSagas.java`) é um `SmartLifecycle` que lê `sagas.estafila` / `sagas.filaanterior` / `sagas.proximafila` do `application-<profile>.yaml` e chama `Messaging.configurarServico(...)` + `iniciarConsumo(...)`. Cada módulo tem sua própria classe-ponte `SagasWiring` (`@Profile("sagas")` + `@Import`) pra trazer `RabbitConfig`/`Messaging` de `sagas-common` sem acoplar o nome do profile à lib — ver [deploy-roles-by-profile.md](deploy-roles-by-profile.md).
  - exceção: em `pagamento-interno`, `sagas.estafila` fica no `application.yaml` — os papéis `web` (webhook) e `pendencias` (job do webhook) publicam nessa mesma fila pra iniciar a SAGA
  - `reservas-interno` lê uma lista, `sagas.consumers`: um processo pode ser vários nós da cadeia do mesmo domínio (mesmo banco), cada um com seu `campoid` (qual id da mensagem é dele); `Messaging` guarda um consumer por fila

## Protocolo de mensagem

- Exchange único `sagas` (tipo `direct`), uma fila por nome lógico (`pagamento`, `hotel`, `voo-ida`, `voo-volta`, `sessaocompra`), roteamento = nome da fila.
- Toda fila é declarada com dead-letter para o exchange `errors_exchange` / routing key `errors` — mensagem rejeitada sem republish cai lá.
- Mensagem é um JSON genérico (`Map<String, Object>`) com um campo `tipo`: `1.0` = `EXECUTE` (seguir adiante), `2.0` = `DESFACA` (compensar, seguir para trás). Default é `EXECUTE` se ausente.
- `basicQos(1)`: cada instância processa uma mensagem por vez (serializa, mas também é teto de throughput).

## Fluxo de sucesso vs. falha (`Messaging.iniciarConsumo`)

`MessageHandler.handle(mensagem)` retorna um `ResultadoHandler(RetornoBroker, Encaminhamento)` — dois eixos ortogonais, o handler controla os dois de forma independente:

- `RetornoBroker`: `ACK` ou `NACK_DLQ` (nack sem requeue, cai no dead-letter).
- `Encaminhamento`: `NENHUM`, `PARA_FRENTE` (publica na `filaProximoServico`, `tipo = EXECUTE`) ou `PARA_TRAS` (publica na `filaServicoAnterior`, `tipo = DESFACA`). O framework sobrescreve o campo `tipo` da mensagem de acordo — o handler não precisa mexer nele, só decidir a direção.

1. Decodifica a mensagem, chama `handler.handle(mensagem)`.
2. **Handler retorna normalmente** → aplica o `RetornoBroker` e o `Encaminhamento` do `ResultadoHandler` (se a fila de destino não existir — ex. fim da cadeia — simplesmente não publica nada).
3. **Handler lança exceção não capturada** (bug/falha sistêmica, não um `ResultadoHandler` de negócio) → sempre `basicNack` (dead-letter) **e**, se existir fila anterior, republica a mesma mensagem com `tipo = DESFACA` nela — disparando a compensação retroativa automaticamente, sem código extra no handler. Esse caminho não passa pelo `ResultadoHandler` — é a rede de segurança pra erro inesperado, não pra desfecho de negócio previsto.

O handler de negócio expressa os casos esperados (sucesso, falha de negócio, falha em obter resposta do externo) via `ResultadoHandler`; só precisa lançar exceção pro caso realmente inesperado — não precisa saber que está numa saga pra disparar rollback distribuído.

## Cadeia

```
pagamento → hotel → voo-ida → voo-volta → sessaocompra
```

- voo em dois nós, um por perna: cada reserva ganha do framework o sucesso, a falha e a compensação, em vez de lógica própria no handler pra "uma confirmou, a outra não"
  - mesmo processo (`voo,sagas`) consumindo as duas filas — um processo por perna custaria outra JVM

Fiação em `sagas.*` de [application-sagas.yaml](../pagamento-interno/src/main/resources/application-sagas.yaml) (`pagamento-interno`) e [application-hotel.yaml](../reservas-interno/src/main/resources/application-hotel.yaml)/[application-voo.yaml](../reservas-interno/src/main/resources/application-voo.yaml) (`reservas-interno`).

**Quem publica não precisa ser nó da cadeia.** Publicar é só ser cliente da fila; o papel `sagas` é quem fica pendurado nela escutando.

- o início da SAGA é o webhook de pagamento (papel `web` de `pagamento-interno`): publica o `EXECUTE` inicial na fila `pagamento`
- `pagamento` é o primeiro nó da cadeia
  - nada o antecede no caminho de execução: a `filaanterior` dele (`sessaocompra`) só serve à reversão (estorno → `sessaocompra`, ver extensão abaixo)
- `sessaocompra` é a ponta final nas duas direções: sem `filaanterior` nem `proximafila`
  - com anterior, uma exceção no `DESFACA` republicaria `DESFACA` pra trás e a reversão entraria em loop

## Extensão — sessaocompra como bookend do anel

Dois nós (`confirma` depois de `voo-volta`, `reverte` antes de `pagamento`) fazem `sessaocompra` participar do mesmo anel de coreografia, reaproveitando este mecanismo em vez de um consumo de fila à parte. Detalhe em [purchase-flow-design.md](purchase-flow-design.md#saga-estendida--sessaocompra-como-bookend-do-anel), diagrama no [README](../README.md#saga-disparada-a-partir-do-webhook-de-pagamento).

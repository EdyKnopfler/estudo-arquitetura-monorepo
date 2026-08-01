# Coreografia SAGA

Implementação própria sobre o cliente Java cru do RabbitMQ (`com.rabbitmq.client`, não Spring AMQP), centralizada em `sagas-common`. É **coreografia**, não orquestração: cada serviço só conhece a fila anterior e a próxima na cadeia, sem um coordenador central.

## Peças

- `RabbitConfig` (`sagas-common`): abre uma única `Connection`/`Channel` por instância a partir de `sagas.rabbithost/port/user/password`.
- `SagasMessaging` (`sagas-common`): declara exchanges/filas e implementa o protocolo de consumo/publicação.
- `SagaMessageHandler`: interface funcional que cada serviço implementa com a lógica de negócio real (`handle(Map<String, Object> mensagem)`).
- Cada `-sagas` (ex.: `reservas-interno-sagas/reservas/ReservasSagas.java`) é um `SmartLifecycle` que lê `sagas.estafila` / `sagas.filaanterior` / `sagas.proximafila` do `application-<profile>.yaml` e chama `SagasMessaging.configurarServico(...)` + `iniciarConsumo(...)`.

## Protocolo de mensagem

- Exchange único `sagas` (tipo `direct`), uma fila por nome lógico (`pagamento`, `hotel`, `voo`), roteamento = nome da fila.
- Toda fila é declarada com dead-letter para o exchange `errors_exchange` / routing key `errors` — mensagem rejeitada sem republish cai lá.
- Mensagem é um JSON genérico (`Map<String, Object>`) com um campo `tipo`: `1.0` = `EXECUTE` (seguir adiante), `2.0` = `DESFACA` (compensar, seguir para trás). Default é `EXECUTE` se ausente.
- `basicQos(1)`: cada instância processa uma mensagem por vez (serializa, mas também é teto de throughput — ver [module-boundaries.md](module-boundaries.md)).

## Fluxo de sucesso vs. falha (`SagasMessaging.iniciarConsumo`)

1. Decodifica a mensagem, chama `handler.handle(mensagem)`.
2. **Sem exceção** → `basicAck`. Se `tipo == EXECUTE`, publica na `filaProximoServico`; se `tipo == DESFACA`, publica na `filaServicoAnterior`. Assim a própria mensagem "sabe" em qual direção está viajando.
3. **Com exceção** → `basicNack` (sem requeue, cai no dead-letter) **e**, se existir fila anterior, republica a mesma mensagem com `tipo = DESFACA` nela — disparando a compensação retroativa automaticamente, sem código extra no handler.

Esse é o mecanismo mais elegante do projeto: o handler de negócio só precisa lançar uma exceção para acionar rollback distribuído; não precisa saber que está numa saga.

## Cadeia atual

```
pagamento → hotel → voo
```

Config real (`reservas-interno-sagas/src/main/resources/application-hotel.yaml` e `-voo.yaml`):
- `hotel`: `filaanterior: pagamento`, `proximafila: voo`
- `voo`: `filaanterior: hotel`, sem `proximafila` (fim da cadeia)

## Status de implementação (importante — mecânica ≠ negócio)

- A mecânica de fila (declarar, consumir, ack/nack, compensação) **funciona**.
- O handler de negócio em `ReservasSagas` (`reservas-interno-sagas`) é um **stub**: só imprime a mensagem recebida (`// TODO fazer o tratamento no nível do negócio`). Nenhuma reserva é confirmada/cancelada a partir da fila ainda.
- **Não existe módulo `pagamento-interno-sagas`** — não há nada publicando a primeira mensagem na fila `pagamento`. O `docker-compose.yml` tem o comentário explícito `# TODO serviço SAGAS de pagamento (interno)`.
- Não há campo de correlação/id de sessão de compra na mensagem ainda — quando o negócio for implementado, a mensagem provavelmente precisa carregar o id da `SessaoCompra` para o handler saber o que confirmar/cancelar.

Ver [todo.md](todo.md) para a lista consolidada.

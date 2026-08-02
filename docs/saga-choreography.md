# Coreografia SAGA

Implementação própria sobre o cliente Java cru do RabbitMQ (`com.rabbitmq.client`, não Spring AMQP), centralizada em `sagas-common`. É **coreografia**, não orquestração: cada serviço só conhece a fila anterior e a próxima na cadeia, sem um coordenador central.

## Peças

- `RabbitConfig` (`sagas-common`): abre uma única `Connection`/`Channel` por instância a partir de `sagas.rabbithost/port/user/password`.
- `SagasMessaging` (`sagas-common`): declara exchanges/filas e implementa o protocolo de consumo/publicação.
- `SagaMessageHandler`: interface funcional que cada serviço implementa com a lógica de negócio real (`handle(Map<String, Object> mensagem)`).
- Cada papel `sagas` (ex.: `reservas-interno/.../sagas/ReservasSagas.java`, `pagamento-interno/.../sagas/PagamentoSagas.java`) é um `SmartLifecycle` que lê `sagas.estafila` / `sagas.filaanterior` / `sagas.proximafila` do `application-<profile>.yaml` e chama `SagasMessaging.configurarServico(...)` + `iniciarConsumo(...)`. Cada módulo unificado tem sua própria classe-ponte `SagasWiring` (`@Profile("sagas")` + `@Import`) pra trazer `RabbitConfig`/`SagasMessaging` de `sagas-common` sem acoplar o nome do profile à lib — ver [module-boundaries.md](module-boundaries.md).

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

Config real (`reservas-interno/src/main/resources/application-hotel.yaml` e `-voo.yaml`):
- `hotel`: `filaanterior: pagamento`, `proximafila: voo`
- `voo`: `filaanterior: hotel`, sem `proximafila` (fim da cadeia)

`pagamento-interno/src/main/resources/application-sagas.yaml`: `estafila: pagamento`, `proximafila: hotel`, sem `filaanterior` (início da cadeia — só existe pra escutar compensação vinda de volta de hotel/voo, não pra receber execução de alguém anterior).

## Status de implementação (importante — mecânica ≠ negócio)

- A mecânica de fila (declarar, consumir, ack/nack, compensação) **funciona** — testada manualmente publicando direto nas filas via management UI do RabbitMQ: uma mensagem `{"tipo":1}` publicada em `hotel` é consumida e repassada corretamente até `voo`.
- O handler de negócio em `ReservasSagas`/`PagamentoSagas` é um **stub**: só imprime a mensagem recebida (`// TODO fazer o tratamento no nível do negócio`). Nenhuma reserva/pagamento é confirmado/cancelado a partir da fila ainda — e, como o stub nunca lança exceção, hoje **não tem como o handler em si disparar uma compensação**; só dá pra forçar isso de fora publicando um corpo inválido (que falha no `decode()`, antes do handler rodar) — só que aí a mensagem já não existe pra ser republicada pra trás (`SagasMessaging` corretamente não compensa sem uma mensagem decodificada), então isso só demonstra o caminho de dead-letter, não a compensação retroativa de verdade. Validar o caminho de compensação com conteúdo de mensagem preservado só vai ser possível quando o handler tiver lógica real capaz de falhar (ou temporariamente forçando uma exceção só pra teste, sem commitar).
- `pagamento-interno` já tem o papel `sagas` (desde a unificação dos módulos), então a fila `pagamento` tem consumidor — mas nada ainda publica a primeira mensagem nela: `PagamentoInternoController.webhookServicoExterno()` continua um método vazio (confirmado: chamar o webhook não gera mensagem nenhuma na fila).
- Não há campo de correlação/id de sessão de compra na mensagem ainda — quando o negócio for implementado, a mensagem provavelmente precisa carregar o id da `SessaoCompra` para o handler saber o que confirmar/cancelar.

Ver [todo.md](todo.md) para a lista consolidada.

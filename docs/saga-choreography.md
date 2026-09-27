# Coreografia SAGA

Implementação própria sobre o cliente Java cru do RabbitMQ (`com.rabbitmq.client`, não Spring AMQP), centralizada em `sagas-common`. É **coreografia**, não orquestração: cada serviço só conhece a fila anterior e a próxima na cadeia, sem um coordenador central.

## Peças

- `RabbitConfig` (`sagas-common`): abre uma única `Connection`/`Channel` por instância a partir de `sagas.rabbithost/port/user/password`.
- `Messaging` (`sagas-common`): declara exchanges/filas e implementa o protocolo de consumo/publicação.
- `MessageHandler`: interface funcional que cada serviço implementa com a lógica de negócio real (`ResultadoHandler handle(Map<String, Object> mensagem)`).
- Cada papel `sagas` (ex.: `reservas-interno/.../sagas/ReservasSagas.java`, `pagamento-interno/.../sagas/PagamentoSagas.java`) é um `SmartLifecycle` que lê `sagas.estafila` / `sagas.filaanterior` / `sagas.proximafila` do `application-<profile>.yaml` e chama `Messaging.configurarServico(...)` + `iniciarConsumo(...)`. Cada módulo tem sua própria classe-ponte `SagasWiring` (`@Profile("sagas")` + `@Import`) pra trazer `RabbitConfig`/`Messaging` de `sagas-common` sem acoplar o nome do profile à lib — ver [deploy-roles-by-profile.md](deploy-roles-by-profile.md).

## Protocolo de mensagem

- Exchange único `sagas` (tipo `direct`), uma fila por nome lógico (`pagamento`, `hotel`, `voo`), roteamento = nome da fila.
- Toda fila é declarada com dead-letter para o exchange `errors_exchange` / routing key `errors` — mensagem rejeitada sem republish cai lá.
- Mensagem é um JSON genérico (`Map<String, Object>`) com um campo `tipo`: `1.0` = `EXECUTE` (seguir adiante), `2.0` = `DESFACA` (compensar, seguir para trás). Default é `EXECUTE` se ausente.
- `basicQos(1)`: cada instância processa uma mensagem por vez (serializa, mas também é teto de throughput — ver [todo.md](todo.md#decisões-em-aberto-não-são-bugs-são-pontos-a-revisitar)).

## Fluxo de sucesso vs. falha (`Messaging.iniciarConsumo`)

`MessageHandler.handle(mensagem)` retorna um `ResultadoHandler(RetornoBroker, Encaminhamento)` — dois eixos ortogonais, o handler controla os dois de forma independente:

- `RetornoBroker`: `ACK` ou `NACK_DLQ` (nack sem requeue, cai no dead-letter).
- `Encaminhamento`: `NENHUM`, `PARA_FRENTE` (publica na `filaProximoServico`, `tipo = EXECUTE`) ou `PARA_TRAS` (publica na `filaServicoAnterior`, `tipo = DESFACA`). O framework sobrescreve o campo `tipo` da mensagem de acordo — o handler não precisa mexer nele, só decidir a direção.

1. Decodifica a mensagem, chama `handler.handle(mensagem)`.
2. **Handler retorna normalmente** → aplica o `RetornoBroker` e o `Encaminhamento` do `ResultadoHandler` (se a fila de destino não existir — ex. fim da cadeia — simplesmente não publica nada).
3. **Handler lança exceção não capturada** (bug/falha sistêmica, não um `ResultadoHandler` de negócio) → sempre `basicNack` (dead-letter) **e**, se existir fila anterior, republica a mesma mensagem com `tipo = DESFACA` nela — disparando a compensação retroativa automaticamente, sem código extra no handler. Esse caminho não passa pelo `ResultadoHandler` — é a rede de segurança pra erro inesperado, não pra desfecho de negócio previsto.

O handler de negócio expressa os casos esperados (sucesso, falha de negócio, falha em obter resposta do externo) via `ResultadoHandler`; só precisa lançar exceção pro caso realmente inesperado — não precisa saber que está numa saga pra disparar rollback distribuído.

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
- O handler de negócio em `ReservasSagas`/`PagamentoSagas` é um **stub**: só imprime a mensagem recebida (`// TODO fazer o tratamento no nível do negócio`). Nenhuma reserva/pagamento é confirmado/cancelado a partir da fila ainda — e, como o stub nunca lança exceção, hoje **não tem como o handler em si disparar uma compensação**; só dá pra forçar isso de fora publicando um corpo inválido (que falha no `decode()`, antes do handler rodar) — só que aí a mensagem já não existe pra ser republicada pra trás (`Messaging` corretamente não compensa sem uma mensagem decodificada), então isso só demonstra o caminho de dead-letter, não a compensação retroativa de verdade. Validar o caminho de compensação com conteúdo de mensagem preservado só vai ser possível quando o handler tiver lógica real capaz de falhar (ou temporariamente forçando uma exceção só pra teste, sem commitar).
- `PagamentoInternoController.webhookServicoExterno()` (papel `web`) publica a primeira mensagem na fila `pagamento`, consumida pelo papel `sagas`. A mensagem ainda só carrega `tipo` + `rastreio`, e o webhook não confirma o pagamento no banco.
- A mensagem só carrega `rastreio` (id opaco de fluxo, gerado no webhook) — ainda sem os ids de negócio que cada handler precisa. Desenho em [purchase-flow-design.md](purchase-flow-design.md#payload-da-mensagem-da-saga).

Ver [todo.md](todo.md) para a lista consolidada.

## Extensão planejada — sessaocompra como bookend do anel

Desenho ainda não implementado: dois nós novos (`confirma` depois de `voo`, `reverte` antes de `pagamento`) fariam `sessaocompra` participar do mesmo anel de coreografia, reaproveitando este mecanismo (handler lança exceção → compensação automática pra trás) em vez de um consumo de fila à parte. Detalhe e diagrama em [purchase-flow-design.md](purchase-flow-design.md).

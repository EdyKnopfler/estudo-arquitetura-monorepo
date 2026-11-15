# A decidir

Decisões em aberto. O que já está decidido e falta implementar fica em [todo.md](todo.md).

## Features por domínio

### Sessão de compra — ponta da SAGA

* [ ] modelagem de status da reversão (`DESFACA` em `sessaocompra`) — [desenho](purchase-flow-design.md#saga-estendida--sessaocompra-como-bookend-do-anel)
* [ ] a considerar na reversão: tirar da sessão as reservas que falharam (a reserva em si já marca a falha — [desenho](purchase-flow-design.md#saga-estendida--sessaocompra-como-bookend-do-anel)) e notificar o usuário, que decide se volta pra tentar outras opções
* [ ] fim da SAGA com a sessão fora de `PAGAMENTO_EFETUADO` (ex.: o `PUT` da sessão ainda com o job de pendências) — provisório: fila de erros, sem encaminhar
  * inclui o `DESFACA` de volta quando `sessaocompra` falha ao confirmar — hoje a fila `sessaocompra` não tem anterior (exceção no `DESFACA` faria loop)

### Pagamentos

* [ ] se consultar e cancelar do externo têm falha proposital (chaos)
* [ ] webhook:
  * [ ] como correlacionar webhook de tentativa antiga — a linha só guarda o `id_externo` vigente
  * [ ] duas tentativas pagas na mesma sessão
* [ ] falha no estorno (cliente cobrado sem viagem) — erro catastrófico, a modelar; provisório: fila de erros, sem reverter a sessão
  * alternativa: DLQ + seguir pra trás até `sessaocompra`, como nas reservas ([princípio](dlq-and-reprocessing.md#compensação-continua-a-falha-vai-pra-dlq))

## Lacunas de arquitetura

### Contrato da mensagem da SAGA

- [ ] contrato da mensagem: `Map` genérico × DTO da fila — [protocolo](saga-choreography.md#protocolo-de-mensagem)

### Erros catastróficos

- [ ] tratamento de erros catastróficos usando a DLQ: hoje os provisórios (falha no estorno; fim da SAGA com a sessão fora do estado) só caem na fila `errors` — o consumo previsto é o [reprocessador](dlq-and-reprocessing.md#reprocessador), mas o desfecho de cada um segue a modelar

### DLQ e reprocessamento

Desenho decidido em [dlq-and-reprocessing.md](dlq-and-reprocessing.md).

- [ ] falha persistente do externo numa etapa (não é bug nosso, mas precisa de tratamento) — provisório: DLQ
  - serviço de retry dentro da janela de validade dos recursos externos
  - falhar depois das tentativas, mover pra um lugar à parte (diferenciar de "precisa reprocessar"), e o reprocessamento é o usuário tentar de novo — tentar com recurso já preso cai em "estado desejado já atingido"
- [ ] reprocessador:
  - [ ] escolher uma mensagem: varrer com `basicGet` sem ack e devolver as outras (DLQ pequena), ou drenar a DLQ pra um banco próprio
  - [ ] limite por motivo: exige o framework publicar na DLQ à mão com a causa no header (+ `ack`) — aí não há `x-death`, e o sinal de reprocessamento precisaria ser outro
  - [ ] autenticação
  - [ ] testes
- [ ] monitoração das `*.dlq` (ex.: API de management, plugin Prometheus) — importante em produção
- [ ] cenários de teste de sistema que mandam mensagem pra DLQ, a partir do webhook

### Estados intermediários

- [ ] estado intermediário que guarda chamada externa (ex.: status de intenção em `Reserva`): como não deixá-lo preso
  - timeout próprio pra cada um, ou limpar a partir do timeout da sessão
- [ ] recurso preso por falha na reversão (reserva confirmada sem TTL) quando o usuário desiste em vez de tentar de novo

### Testes

- [ ] RabbitMQ de teste é um container só (reuse) pra `reservas-interno` e `pagamento-interno` — com consumidor real, filas de contextos diferentes podem se misturar; avaliar isolar (vhost por módulo)

### Pontos a revisitar (não são bugs)

- pagamento como nó só de repasse no sucesso — [cadeia](saga-choreography.md#cadeia)
- prioridade bem baixa: relógio da sessão na criação × na primeira pré-reserva — [Dois timeouts](purchase-flow-design.md#dois-timeouts)
- `basicQos(1)` como teto de throughput — revisitar com medição de carga real ([protocolo](saga-choreography.md#protocolo-de-mensagem))
- renomear as envs de chave JWT (ex.: `CLIENTE_JWT_PUBLIC_KEY`) se confundir — [credenciais](security-and-auth.md#credenciais-de-dev)
- Sem captura/agregação centralizada de log — cada instância só loga local (SLF4J). Fora de escopo por agora; talvez um dia uma infra pra isso (sidecar → Elasticsearch, CloudWatch ou o que estiver à mão).
- segurança que assume localhost — revisar item a item ao desenhar deploy de produção ([limitações](security-and-auth.md#limitações-conhecidas))

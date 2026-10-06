# Pendências

Lista detalhada do que falta. O [README.md](../README.md) só resume em que pé está cada módulo e aponta pra cá.

## Features por domínio

Como `sessaocompra` se conecta com os módulos abaixo (pré-reserva, pagamento, SAGA) está desenhado em [purchase-flow-design.md](purchase-flow-design.md).

* [ ] **Timeout:**
  * [ ] sessão em `INICIADA` expirada (`TimeoutTask`, já agendado): **cancela e desfaz as pré-reservas efetuadas**, via REST em `reservas-interno` — não é SAGA
  * [ ] pagamento não efetuado a tempo (`TimeoutPagamentoTask`): mesma operação do cancelamento (pede cancelamento ao externo; já pago segue o fluxo) — ver [purchase-flow-design.md](purchase-flow-design.md#dois-timeouts)
  * [ ] modelar os registros de tempo: o tempo da sessão não conta enquanto corre o do pagamento
  * [ ] sessão presa em `CRIANDO_PAGAMENTO` (front desistiu de retentar, ou processo caiu no meio): nenhum timeout pega — destino em aberto

* [ ] **Sessão de compra — ponta da SAGA** (decidido, ver [purchase-flow-design.md](purchase-flow-design.md#saga-estendida--sessaocompra-como-bookend-do-anel)):
  * [ ] papel `sagas` em `sessaocompra` (passa a depender de `sagas-common`), fila `sessaocompra`; `voo.proximafila` e `pagamento.filaanterior` apontando pra ela
  * [ ] `EXECUTE` (fim da cadeia de sucesso): marca `VIAGEM_RESERVADA`
  * [ ] `DESFACA` (fim da compensação — cancelamento explícito do que foi fechado): volta a sessão pra `INICIADA` e reinicia o timer de expiração
    * [ ] **decidir** a modelagem de status da reversão:
      * diferenciar falha de negócio × erro de execução (inclui serviço externo) — status próprio (`REVERTENDO`?) ou informação no payload
      * terminar em `ERRO` depende dessa distinção (erro de execução, não falha de negócio)
      * [ ] diagramas do README só depois de fechar essa modelagem
    * a considerar: remover as reservas que falharam, resetar o tempo e notificar o usuário, que decide se volta pra tentar outras opções

* [ ] **Pagamentos** — fluxo, criação desenhada em [purchase-flow-design.md](purchase-flow-design.md#criação-do-pagamento):
  * [X] criação no interno (`PUT /pagamentos/{idSessao}`, chamado por `sessaocompra`; unique em `id_sessao_compra`): pagamento por sessão, intenção gravada antes do externo, troca de tentativa até obter URL, depois devolve sempre a mesma
    * [X] chave de idempotência por tentativa: repetida em falha, trocada quando a transação expira no gateway
  * [X] `iniciando-pagamento` idempotente ([cadeia](purchase-flow-design.md#cadeia-idempotente)): falha não reverte a sessão; em `CRIANDO_PAGAMENTO`/`EFETUANDO_PAGAMENTO` repete o `PUT`; devolve a URL
  * [ ] cancelar pagamento, com ou sem URL ([desenho](purchase-flow-design.md#cancelamento-e-prazo--gateway-como-juiz)): endpoint em `sessaocompra` e em `pagamento-interno`; com URL, pede cancelamento ao externo (já pago segue o fluxo); o `PUT` seguinte abre tentativa nova com os ids de reserva atuais
  * [X] **externo:** criar ([premissas](purchase-flow-design.md#premissas-do-gateway-simulado-pagamento-externo))
    * [X] banco próprio (`externo_pagamento`) e tabela `pagamentos`
    * [X] _deve falhar às vezes de propósito_ — metade antes de gravar, metade depois
    * [X] devolve URL de pagamento fictícia
    * [X] par (chave, cliente): cria só se não existe; se existe, devolve o estado atual
    * [X] prazo por relógio a partir da criação
    * [X] não chama mais o webhook
  * [X] **externo:** pagar — sorteia aceito/recusado e chama o webhook síncrono; recusa mantém a transação pendente; expirado responde expirado, sem webhook
  * [X] **externo:** consultar — devolve a situação atual da transação ([reconciliação](reconciliation.md))
  * [X] **externo:** cancelar transação pendente
  * [ ] **decidir** se consultar e cancelar do externo têm falha proposital (chaos)
  * [X] criar no interno: gateway devolve transação expirada → tentativa nova na mesma chamada
  * [ ] URL já obtida que venceu no externo → tentativa nova (consultar antes de trocar)
    * [ ] **decidir** quando consultar: todo `PUT` em `AGUARDANDO_PAGAMENTO` (simples, interno não conhece o prazo do externo) ou só depois do vencimento (exige o criar devolver o vencimento)
    * [ ] **decidir** o que o `PUT` devolve quando a consulta diz pago — "sucesso até o front" pede status na resposta do interno, repassado pelo `sessaocompra`
  * [ ] implementação do webhook no interno
    * [ ] validar assinatura/origem e proteger contra reprocessamento
    * [ ] pagamento aceito é reconciliado a favor da venda, mesmo fora da tentativa vigente ([reconciliação](reconciliation.md))
      * [ ] **decidir** como correlacionar webhook de tentativa antiga — a linha só guarda o `id_externo` vigente
      * [ ] **decidir** duas tentativas pagas na mesma sessão
    * [ ] outbox + transição de status do pagamento pelo webhook — ver [Dual-write](#dual-write-pagamentoreservas-outbox-das-reservas-descartado--ver-desenho)
    * [ ] sucesso: sessão vai pra `PAGAMENTO_EFETUADO` — mecanismo em aberto (hoje `PUT /sessoes/{id}/pagamento-efetuado`, sem auth de serviço — ver [Testes](#testes))
    * [ ] sucesso: publica `EXECUTE` na fila `pagamento` com os ids internos ([desenho do payload](purchase-flow-design.md#payload-da-mensagem-da-saga))
    * [ ] **decidir** o que o interno faz com o webhook de recusa (no externo, a transação continua pendente)
  * [ ] **sagas:** eventos de confirmação e cancelamento
    * [X] recebe do anterior e passa para o próximo (filas de "entrada" e saída)
    * [ ] compensação: estorna e repassa `DESFACA` até `sessaocompra` (`filaanterior: sessaocompra`, ver [saga-choreography.md](saga-choreography.md#cadeia))
  * [ ] **externo:** endpoint de estorno, _deve falhar às vezes de propósito_
  * [ ] **externo:** latência aleatória (chaos) — hoje só sorteia falha
  * [ ] Testes integrados
    * [ ] Requisição > Externo > Pagar > Webhook
    * [ ] Encaminha sucesso para outro serviço
    * [ ] Notificação de falha por outro serviço

* [ ] **Hotel:**
  * [X] **web:** interação com usuário (pré-reservas) — chamado por `sessaocompra`, não o inverso
    * [X] chama endpoints do serviço externo
    * [ ] cancelamento por timeout da sessão (chamado pelo `TimeoutTask` de `sessaocompra`, via REST)
  * [ ] **sagas:** eventos de confirmação e cancelamento por reversão
    * [X] fiação das filas de "entrada" e saída
    * [ ] chama endpoints do serviço externo
  * [X] **externo:** simula serviço externo, **introduz erros aleatórios**
    * [X] **pré-reserva:** cria reserva sem confirmação
    * [X] **confirmação:** confirma pré-reservas feitas _há menos de 15 minutos_
    * [X] **cancelamento:** cancela pré-reservas
    * [X] _deve falhar às vezes de propósito_
  * [ ] Testes integrados
    * [X] Requisição > Externo (`ReservasExternoServiceIntegrationTest`)
    * [ ] Encaminha sucesso para outro serviço
    * [ ] Notificação de falha por outro serviço

* [ ] **Voo:** para voos ida e volta, mesma estrutura de _Hotel_
  * [X] **web:** interação com usuário (pré-reservas) — chamado por `sessaocompra`, não o inverso
    * [X] chama endpoints do serviço externo
    * [ ] cancelamento por timeout da sessão (chamado pelo `TimeoutTask` de `sessaocompra`, via REST)
  * [ ] **sagas:** eventos de confirmação e cancelamento por reversão
    * [X] fiação das filas de "entrada" e saída
    * [ ] chama endpoints do serviço externo
    * [ ] agir nas duas reservas (ida + volta) a partir de uma mensagem — forma não desenhada
    * [ ] fim da cadeia de sucesso: publica `EXECUTE` na fila `sessaocompra` (confirma a viagem); recebe `DESFACA` de volta se `sessaocompra` falhar ao confirmar
  * [X] **externo:** simula serviço externo, **introduz erros aleatórios**
    * [X] **pré-reserva:** cria reserva sem confirmação
    * [X] **confirmação:** confirma pré-reservas feitas _há menos de 15 minutos_
    * [X] **cancelamento:** cancela pré-reservas
    * [X] _deve falhar às vezes de propósito_
  * [ ] Testes integrados
    * [X] Requisição > Externo (mesmo código de _Hotel_, só muda o profile — coberto por `ReservasExternoServiceIntegrationTest`)
    * [ ] Encaminha sucesso para outro serviço
    * [ ] Notificação de falha por outro serviço

* [ ] **Clientes:** refresh token (TODO em `AuthController`)
* [ ] **Pagamentos — regra de precificação:** `metodo`/`valor` hoje fixos em `PagamentoService`

## Lacunas de arquitetura

Transversais ou de "amarração", não de regra de negócio de um domínio — levantadas numa revisão de arquitetura em 2026-08-01.

### Dual-write pagamento/reservas (outbox das reservas descartado — ver desenho)

- [ ] **Outbox no webhook de pagamento** (decidido — ponto crucial do fluxo). Confirmar pagamento no banco + publicar a 1ª mensagem da SAGA é um dual-write clássico. Status do pagamento em `pagamentos` faz parte disso. **Ordem:** depois do item de reservas abaixo — esse expõe a versão geral do problema (dual-write contra sistema externo não-idempotente), o outbox do webhook é um caso mais estreito do mesmo tema. **Nota:** a criação do pagamento é outro dual-write, resolvido com intenção gravada antes da chamada — ver [purchase-flow-design.md](purchase-flow-design.md#dual-write-na-criação).
- [ ] **Robustez do passo de confirmar/reverter `Reserva` em `reservas-interno`.** Desenho fechado (axiomas do `reservas-externo`, esboço de arquitetura) em [purchase-flow-design.md](purchase-flow-design.md). Quebra em:
  - [ ] Endpoint de consulta de estado (`consultar`) em `reservas-externo` — desambigua timeout/falha de infra sem exigir idempotência do lado de lá.
  - [ ] Endpoint `desconfirmar`/estorno em `reservas-externo` — falta pra reverter uma `Reserva` já confirmada.
  - [x] ~~`Messaging`: novo desfecho "erro tratado (negócio)" → ack + publica pra trás, sem passar pela DLQ.~~ — `ResultadoHandler.ack(PARA_TRAS)`.
  - [x] ~~`Messaging`: novo desfecho "ack puro, sem publish" — falha em obter resposta do externo; resolução fica pra task.~~ — `ResultadoHandler.ack(NENHUM)`.
  - [ ] Status de intenção + contador de tentativas em `Reserva` (nomes em aberto) — transição idempotente (`WHERE` aceita estado anterior e o alvo).
  - [ ] Task de retentativa: acha reservas em intenção, resolve via `consultar`, publica a sequência SAGA pra frente/trás — marca status terminal *depois* de publicar, não antes (retry seguro se cair no meio, downstream já tolera duplicata). Ao estourar o limite de tentativas: publica manualmente na fila de erros (mensagem original já foi ackeada, não tem nack pra dar).
  - [ ] Handler de negócio em `ReservasSagas` usando esse fluxo.

### Contrato da mensagem da SAGA

- [ ] **Decidir** o contrato da mensagem ([module-boundaries.md](module-boundaries.md#contrato-tipado-para-a-mensagem-da-saga)): `Map<String, Object>` genérico (`Messaging`) ou DTO próprio da fila (ids de correlação da sessão + ids de reserva/pagamento)
  - a favor do DTO: não reaproveitar entidade JPA nem DTO de REST — contratos com motivos de mudança diferentes
  - ganha peso com os ids de negócio na mensagem ([payload](purchase-flow-design.md#payload-da-mensagem-da-saga))

### Testes

Estratégia e comandos: [testing-strategy.md](testing-strategy.md). Integrados por domínio: seção [Features por domínio](#features-por-domínio).

- [ ] **Depois de separar criar/pagar no externo** ([desenho](purchase-flow-design.md#criação-do-pagamento)):
  - webhook publica `EXECUTE` na fila `pagamento`: fila temporária ligada à exchange `sagas` + `basicGet`
- [ ] **Com os handlers da SAGA e o timeout implementados:**
  - handlers de `ReservasSagas`/`PagamentoSagas` ponta a ponta — `ReservasSagasTest` já cobre os desfechos de `confirmar`/`cancelar` isolados; falta com id real na mensagem, e `PagamentoSagas`
  - jobs de `TimeoutTask` em `sessaocompra`
  - integrados "encaminha sucesso" / "notificação de falha" de [Features por domínio](#features-por-domínio) (pagamento, hotel, voo)
  - RabbitMQ de teste é um container só (reuse) pra `reservas-interno` e `pagamento-interno` — com consumidor real, filas de contextos diferentes podem se misturar; avaliar isolar (vhost por módulo)
- [ ] **`PUT /sessoes/{id}/pagamento-efetuado` sem teste de propósito** — qualquer cliente com JWT válido marca qualquer sessão em `EFETUANDO_PAGAMENTO` como `PAGAMENTO_EFETUADO` (sem ownership, sem autenticação de serviço). Teste agora congelaria esse buraco; testar quando virar consumidor de fila (ver comentário no controller e [purchase-flow-design.md](purchase-flow-design.md)).

### Hygiene / housekeeping

- [ ] `Messaging.iniciarConsumo` dá ack/nack da mensagem recebida **antes** de publicar a próxima na cadeia — queda nesse intervalo perde a publicação sem redelivery (a mensagem de entrada já foi consumida). Corrigir junto com o trabalho de robustez dos handlers (mesma área).

### Decisões em aberto (não são bugs, são pontos a revisitar)

- `basicQos(1)` limita cada instância de SAGA a processar uma mensagem por vez — teto de throughput conhecido, revisitar se/quando houver medição de carga real.
- `JWT_PUBLIC_KEY`/`JWT_PRIVATE_KEY` no `.env`/`.env.clientes` não seguem o padrão `<SERVIÇO>_ID`/`_SECRET` do resto do arquivo (são infra compartilhada tipo `DB_HOST`, não credencial de um par específico) — considerar renomear pra algo tipo `CLIENTE_JWT_PUBLIC_KEY` se ficar confuso.
- Sem captura/agregação centralizada de log — cada instância só loga local (SLF4J). Fora de escopo por agora; talvez um dia uma infra pra isso (sidecar → Elasticsearch, CloudWatch ou o que estiver à mão).
- **Segurança que hoje assume "só roda em localhost"** — sem desenho de deploy de produção ainda, cada item abaixo fica pendente de revisão isolada quando esse desenho começar:
  - JDWP (`JAVA_TOOL_OPTIONS=-agentlib:jdwp=...,address=*:PORT`) exposto e publicado no host em todo serviço, pra attach de debugger — JDWP não tem autenticação (debugger anexado = RCE). Aceitável hoje (dev local); não pode existir fora de `localhost`.
  - Secrets vivem em `.env`/`.env.<serviço>` sem gestão real (vault, secrets manager de nuvem) — aceitável hoje porque são credenciais de dev descartáveis (só `.env*.example` é versionado).
  - Nenhuma comunicação usa TLS (front↔`clientes`/`sessaocompra`, serviço↔serviço, app↔Postgres/RabbitMQ) — tudo HTTP/AMQP puro na rede Docker local.
  - Sem rate limit/lockout em `/login` (`clientes`) — decisão consciente de deixar como responsabilidade de borda (gateway/WAF), não da aplicação: throttling por IP não precisa entender o payload, é mais barato bloqueado antes da app, e ajusta sem redeploy. Reavaliar se algum dia precisar de lockout por `email` (semântica de negócio que a borda não enxerga sozinha).

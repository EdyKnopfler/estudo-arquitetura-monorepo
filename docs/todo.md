# Pendências

O que já está decidido e falta implementar. Decisões em aberto: [to-decide.md](to-decide.md). O [README.md](../README.md) só resume em que pé está cada módulo.

## Features por domínio

Como `sessaocompra` se conecta com os módulos abaixo (pré-reserva, pagamento, SAGA) está desenhado em [purchase-flow-design.md](purchase-flow-design.md).

* [ ] **Timeout:**
  * [ ] sessão em `INICIADA` expirada (`TimeoutTask`, já agendado): **cancela e desfaz as pré-reservas efetuadas**, via REST em `reservas-interno` — não é SAGA
  * [ ] pagamento não efetuado a tempo (`TimeoutPagamentoTask`): mesma operação do cancelamento (pede cancelamento ao externo; já pago segue o fluxo) — ver [purchase-flow-design.md](purchase-flow-design.md#dois-timeouts)
  * [ ] modelar os registros de tempo: o tempo da sessão não conta enquanto corre o do pagamento
    * timeout da sessão é pra sessão abandonada: com pagamento criado, não pega mais
    * [ ] timeout do pagamento reinicia a cada link criado — é pra pagamento criado e abandonado, mas tem que dar tempo de pagar
    * [ ] pré-reservas vencem depois da janela de pagamento (X+Y): têm que durar escolher tudo *e* pagar — hoje pré-reserva, sessão e link são todos 15 min

* [ ] **Sessão de compra — ponta da SAGA** (decidido, ver [purchase-flow-design.md](purchase-flow-design.md#saga-estendida--sessaocompra-como-bookend-do-anel)):
  * [ ] papel `sagas` em `sessaocompra` (passa a depender de `sagas-common`), fila `sessaocompra`; `voo.proximafila` e `pagamento.filaanterior` apontando pra ela
  * [ ] `EXECUTE` (fim da cadeia de sucesso): marca `VIAGEM_RESERVADA`
  * [ ] `DESFACA` (fim da compensação — cancelamento explícito do que foi fechado): volta a sessão pra `INICIADA` e reinicia o timer de expiração
    * modelagem de status da reversão: ver [to-decide.md](to-decide.md)

* [ ] **Pagamentos** — fluxo, criação desenhada em [purchase-flow-design.md](purchase-flow-design.md#criação-do-pagamento):
  * [X] criação no interno (`PUT /pagamentos/{idSessao}`, chamado por `sessaocompra`; unique em `id_sessao_compra`): pagamento por sessão, intenção gravada antes do externo, troca de tentativa até obter URL, depois devolve sempre a mesma
    * [X] chave de idempotência por tentativa: repetida em falha, trocada quando a transação expira no gateway
  * [ ] **avançar rumo ao pagamento** (`iniciando-pagamento` → `PUT /pagamentos/{idSessao}`), orientado a estado ([diretriz](state-driven-endpoints.md))
    * [X] idempotente ([cadeia](purchase-flow-design.md#cadeia-idempotente)): falha não reverte a sessão; em `CRIANDO_PAGAMENTO`/`EFETUANDO_PAGAMENTO` repete o `PUT`
    * [ ] caminho feliz: resposta traz a etapa (URL + vencimento), não só a URL
    * casos, um por vez:
      * [X] (interno) sem pagamento criado → cria e devolve o link
      * [X] (interno) pagamento já criado, link perdido → devolve o mesmo
      * [X] (interno) criação sem resposta, transação já expirada lá → tentativa nova na mesma chamada
      * [ ] (interno) link perto de vencer → cancela no externo e entrega outro
        * link recente é entregue sem consultar (ex.: prazo de 15 min, criado há mais de 10 → renova)
        * âncora (relógio nosso × vencimento do externo): ver [to-decide.md](to-decide.md)
        * outra aba com o link antigo aberto: renovado, o antigo é rejeitado no gateway — vale o link vigente
      * [ ] (interno) ao renovar, o cancelamento descobre que já está pago → responde "já pago" e dispara a SAGA ([reconciliação](reconciliation.md))
      * [X] (sessaocompra) faltam pré-reservas → complete o pedido antes de continuar
      * [ ] (sessaocompra) sessão expirada (só sem link de pagamento válido) → pré-reservas canceladas, inicie outra sessão
  * [ ] **voltar ao pedido** (cancelar o pagamento corrente → sessão `INICIADA`), orientado a estado ([diretriz](state-driven-endpoints.md), [desenho](purchase-flow-design.md#cancelamento-e-prazo--gateway-como-juiz)): endpoint em `sessaocompra` e em `pagamento-interno`; o avanço seguinte abre tentativa nova com os ids de reserva atuais
    * casos, um por vez:
      * [ ] (interno) link criado → cancela no externo
        * [ ] (externo) ainda não expirado → cancelado
        * [ ] (externo) já expirado → nada a fazer
      * [ ] (interno) sem link → nada a fazer — que falha no fluxo da sessão leva a isso?
      * [ ] (interno) já pago → segue o fluxo da venda ([desenho](purchase-flow-design.md#cancelamento-e-prazo--gateway-como-juiz)); alternativa levantada: estornar e cancelar mesmo
  * [X] **externo:** criar ([premissas](purchase-flow-design.md#premissas-do-gateway-simulado-pagamento-externo))
    * [X] banco próprio (`externo_pagamento`) e tabela `pagamentos`
    * [X] _deve falhar às vezes de propósito_ — metade antes de gravar, metade depois
    * [X] devolve URL de pagamento fictícia
    * [X] par (chave, cliente): cria só se não existe; se existe, devolve o estado atual
    * [X] prazo por relógio a partir da criação
    * [X] não chama mais o webhook
  * [X] **externo:** pagar — sorteia aceito/recusado e chama o webhook síncrono; recusa mantém a transação pendente; expirado responde expirado, sem webhook
    * [X] link expirado ou cancelado: rejeita — o front volta à compra e avança de novo (link novo)
  * [X] **externo:** consultar — devolve a situação atual da transação ([reconciliação](reconciliation.md))
  * [X] **externo:** cancelar transação pendente
  * [ ] implementação do webhook no interno
    * [ ] validar assinatura/origem e proteger contra reprocessamento
    * [ ] pagamento aceito é reconciliado a favor da venda, mesmo fora da tentativa vigente ([reconciliação](reconciliation.md))
      * tentativa antiga e duas tentativas pagas: ver [to-decide.md](to-decide.md)
    * [ ] outbox + transição de status do pagamento pelo webhook — ver [Dual-write](#dual-write-pagamentoreservas-outbox-das-reservas-descartado--ver-desenho)
    * [ ] sucesso: sessão vai pra `PAGAMENTO_EFETUADO` — mecanismo: ver [to-decide.md](to-decide.md) (hoje `PUT /sessoes/{id}/pagamento-efetuado`, sem auth de serviço — ver [Testes](#testes))
    * [ ] sucesso: publica `EXECUTE` na fila `pagamento` com os ids internos ([desenho do payload](purchase-flow-design.md#payload-da-mensagem-da-saga))
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
    * [ ] agir nas duas reservas (ida + volta) a partir de uma mensagem — forma: ver [to-decide.md](to-decide.md)
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

### Testes

Estratégia e comandos: [testing-strategy.md](testing-strategy.md). Integrados por domínio: seção [Features por domínio](#features-por-domínio).

- [ ] **Depois de separar criar/pagar no externo** ([desenho](purchase-flow-design.md#criação-do-pagamento)):
  - webhook publica `EXECUTE` na fila `pagamento`: fila temporária ligada à exchange `sagas` + `basicGet`
- [ ] **Com os handlers da SAGA e o timeout implementados:**
  - handlers de `ReservasSagas`/`PagamentoSagas` ponta a ponta — `ReservasSagasTest` já cobre os desfechos de `confirmar`/`cancelar` isolados; falta com id real na mensagem, e `PagamentoSagas`
  - jobs de `TimeoutTask` em `sessaocompra`
  - integrados "encaminha sucesso" / "notificação de falha" de [Features por domínio](#features-por-domínio) (pagamento, hotel, voo)
- [ ] **`PUT /sessoes/{id}/pagamento-efetuado` sem teste de propósito** — qualquer cliente com JWT válido marca qualquer sessão em `EFETUANDO_PAGAMENTO` como `PAGAMENTO_EFETUADO` (sem ownership, sem autenticação de serviço). Teste agora congelaria esse buraco; testar quando virar consumidor de fila (ver comentário no controller e [purchase-flow-design.md](purchase-flow-design.md)).

### Hygiene / housekeeping

- [ ] `Messaging.iniciarConsumo` dá ack/nack da mensagem recebida **antes** de publicar a próxima na cadeia — queda nesse intervalo perde a publicação sem redelivery (a mensagem de entrada já foi consumida). Corrigir junto com o trabalho de robustez dos handlers (mesma área).

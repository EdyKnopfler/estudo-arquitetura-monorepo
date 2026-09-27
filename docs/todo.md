# Pendências

Lista detalhada do que falta. O [README.md](../README.md) só resume em que pé está cada módulo e aponta pra cá.

## Features por domínio

Como `sessaocompra` se conecta com os módulos abaixo (pré-reserva, pagamento, SAGA) está desenhado em [purchase-flow-design.md](purchase-flow-design.md).

**Foco atual:** fluxo do pagamento — efetuar → `pagamento-externo` → webhook.

* [ ] **Timeout:**
  * [ ] sessão em `INICIADA` expirada (`TimeoutTask`, já agendado): **cancela e desfaz as pré-reservas efetuadas**, via REST em `reservas-interno` — não é SAGA
  * [ ] pagamento não efetuado a tempo (`TimeoutPagamentoTask`): cancela o pagamento e volta a sessão pra `INICIADA` — ver [purchase-flow-design.md](purchase-flow-design.md#dois-timeouts)
  * [ ] modelar os registros de tempo: o tempo da sessão não conta enquanto corre o do pagamento
  * [ ] sessão presa em `CRIANDO_PAGAMENTO` (processo caiu no meio): nenhum timeout pega — destino em aberto

* [ ] **Sessão de compra — ponta da SAGA** (decidido, ver [purchase-flow-design.md](purchase-flow-design.md#saga-estendida--sessaocompra-como-bookend-do-anel)):
  * [ ] papel `sagas` em `sessaocompra` (passa a depender de `sagas-common`), fila `sessaocompra`; `voo.proximafila` e `pagamento.filaanterior` apontando pra ela
  * [ ] `EXECUTE` (fim da cadeia de sucesso): marca `VIAGEM_RESERVADA`
  * [ ] `DESFACA` (fim da reversão, ou pagamento recusado no webhook): volta a sessão pra `INICIADA` e reinicia o timer de expiração
    * [ ] **decidir** a modelagem de status da reversão:
      * diferenciar falha de negócio × erro de execução (inclui serviço externo) — status próprio (`REVERTENDO`?) ou informação no payload
      * terminar em `ERRO` depende dessa distinção (erro de execução, não falha de negócio)
      * [ ] diagramas (README, purchase-flow-design) só depois de fechar essa modelagem
    * a considerar: remover as reservas que falharam, resetar o tempo e notificar o usuário, que decide se volta pra tentar outras opções

* [ ] **Pagamentos** — fluxo (foco atual):
  * [X] criação no interno (`POST /pagamentos`, chamado por `sessaocompra`)
  * [X] chamada ao externo (`/efetuar`, _deve falhar às vezes de propósito_)
  * [ ] chamada ao webhook pelo externo — pendente de ajuste no código (TODO em `PagamentoController` de `pagamento-externo`)
  * [ ] implementação do webhook no interno
    * [ ] validar assinatura/origem e proteger contra reprocessamento
    * [ ] outbox + status do pagamento em `pagamentos` — ver [Dual-write](#dual-write-pagamentoreservas-outbox-das-reservas-descartado--ver-desenho)
    * [ ] sucesso: sessão vai pra `PAGAMENTO_EFETUADO` — mecanismo em aberto (hoje `PUT /sessoes/{id}/pagamento-efetuado`, sem auth de serviço — ver [Testes](#testes))
    * [ ] sucesso: publica `EXECUTE` na fila `pagamento` com os ids internos ([desenho do payload](purchase-flow-design.md#payload-da-mensagem-da-saga))
    * [ ] falha: publica `DESFACA` direto na fila `sessaocompra` (nada a desfazer em hotel/voo/pagamento ainda)
  * [ ] **sagas:** eventos de confirmação e cancelamento
    * [X] recebe do anterior e passa para o próximo (filas de "entrada" e saída)
    * [ ] compensação: estorna e repassa `DESFACA` até `sessaocompra` (`filaanterior: sessaocompra`, ver [saga-choreography.md](saga-choreography.md#cadeia))
  * [ ] **externo:** endpoint de estorno, _deve falhar às vezes de propósito_
  * [ ] **externo:** latência aleatória (chaos) — hoje só sorteia falha
  * [ ] Testes integrados
    * [ ] Requisição > Externo > Webhook
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

- [ ] **Outbox no webhook de pagamento** (decidido — ponto crucial do fluxo). Confirmar pagamento no banco + publicar a 1ª mensagem da SAGA é um dual-write clássico. Status do pagamento em `pagamentos` faz parte disso. **Ordem:** depois do item de reservas abaixo — esse expõe a versão geral do problema (dual-write contra sistema externo não-idempotente), o outbox do webhook é um caso mais estreito do mesmo tema. **Nota:** não confundir com `sessaocompra.iniciarPagamento` → `pagamento-interno` (já implementado) — ali não há dual-write, porque a linha em `pagamentos` só é salva depois do `/efetuar` responder; falha nessa chamada só reverte o status local de `SessaoCompra` (`reverterPagamento`) e devolve erro pro front-end tentar de novo.
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

- [x] ~~Cobertura ~zero (só `contextLoads`)~~ — testes de microsserviço em todos os módulos web + integrados de contrato interno↔externo (`ReservasExternoServiceIntegrationTest`, `PagamentoExternoServiceIntegrationTest`).
- [x] ~~Testes automatizados para `web-base`~~ — decisões em [web-base-hardening.md](web-base-hardening.md).
- [x] ~~Testes automatizados para `sagas-common`~~ — `MessagingTest` (desfechos do `ResultadoHandler` e compensação por exceção).
- [ ] **Depois de desacoplar o webhook** (TODO em `PagamentoController` de `pagamento-externo`):
  - reativar caso `SUCESSO` de `PagamentoExternoServiceIntegrationTest` e a classe `PagamentoCriacaoIntegradoTest` (hoje `@Disabled`); conferir o banco via JDBC
  - "recusado" aleatório do `WebhookService` respeitar a simulação (`SimuladorDeTeste`)
  - corpo do webhook com `idTransacao`
  - webhook publica `EXECUTE` na fila `pagamento`: fila temporária ligada à exchange `sagas` + `basicGet`
- [ ] **Com os handlers da SAGA e o timeout implementados:**
  - handlers de `ReservasSagas`/`PagamentoSagas` ponta a ponta — `ReservasSagasTest` já cobre os desfechos de `confirmar`/`cancelar` isolados; falta com id real na mensagem, e `PagamentoSagas`
  - jobs de `TimeoutTask` em `sessaocompra`
  - integrados "encaminha sucesso" / "notificação de falha" de [Features por domínio](#features-por-domínio) (pagamento, hotel, voo)
  - RabbitMQ de teste é um container só (reuse) pra `reservas-interno` e `pagamento-interno` — com consumidor real, filas de contextos diferentes podem se misturar; avaliar isolar (vhost por módulo)
- [ ] **`PUT /sessoes/{id}/pagamento-efetuado` sem teste de propósito** — qualquer cliente com JWT válido marca qualquer sessão em `EFETUANDO_PAGAMENTO` como `PAGAMENTO_EFETUADO` (sem ownership, sem autenticação de serviço). Teste agora congelaria esse buraco; testar quando virar consumidor de fila (ver comentário no controller e [purchase-flow-design.md](purchase-flow-design.md)).

### Hygiene / housekeeping

- [x] ~~`.env` está commitado no git com credenciais de dev.~~ — `.env`/`.env.clientes` viraram `.env.example`/`.env.clientes.example` versionados; reais fora do índice via `.gitignore` (`.env*` / `!.env*.example`, cobre automaticamente qualquer `.env.<serviço>` futuro sem editar o padrão). Histórico não foi reescrito — decisão consciente, são credenciais de dev descartáveis.
- [x] ~~Arquivo órfão `reservas-interno-web/Dockerfile copy`~~ — resolvido de graça pela unificação de `reservas-interno` (o diretório antigo, e o arquivo órfão junto, deixaram de existir).
- [x] ~~`pagamento-interno-web/application.yaml` tinha placeholder malformado~~ — `${FRONT_END_ID}:frontEndId}` (faltava o `:` dentro das chaves) e `FRONT_END_ID`/`FRONT_END_SECRET` não estavam no `.env`, então a aplicação não subia (placeholder não resolvido). Corrigido pra `${FRONT_END_ID:frontEndId}` na migração pra `pagamento-interno`.
- [ ] `Messaging.iniciarConsumo` dá ack/nack da mensagem recebida **antes** de publicar a próxima na cadeia — queda nesse intervalo perde a publicação sem redelivery (a mensagem de entrada já foi consumida). Corrigir junto com o trabalho de robustez dos handlers (mesma área).
- [x] ~~Flyway rodando em todos os papéis, não só `web`~~ — regressão introduzida pela unificação dos módulos: antes, `-sagas`/`-timeout` nem tinham Flyway como dependência (só `-web` migrava); ao virar um artefato só, o pom passou a trazer Flyway incondicionalmente pra qualquer profile. Isso só virou bug visível em `sessaocompra-timeout` (pool `maximum-pool-size: 1` herdado do módulo antigo — Flyway precisa de 2 conexões simultâneas pra coordenação de lock durante a migration, e travava contra si mesmo até estourar timeout de 30s); em `reservas-interno`/`pagamento-interno` o pool nunca foi reduzido pro papel `sagas` (ficou em 10), então a mesma corrida nunca chegou a falhar — mas o problema de fundo (múltiplas instâncias tentando migrar ao mesmo tempo) existia igual, só mascarado. Corrigido com `spring.flyway.enabled: false` explícito nos profiles `sagas`/`timeout` dos três domínios, restaurando "só uma instância migra" como já era antes do refactor.

### Refactor planejado (sessão futura dedicada)

- [x] ~~Unificar `-common`/`-web`/`-sagas` de reservas num único artefato~~ — feito, ver `reservas-interno` e [deploy-roles-by-profile.md](deploy-roles-by-profile.md).
- [x] ~~Fazer o mesmo para pagamento~~ — feito, ver `pagamento-interno`. O papel `sagas` foi criado do zero (nunca existira como módulo). Mecanismo documentado em [deploy-roles-by-profile.md](deploy-roles-by-profile.md).
- [x] ~~Fazer o mesmo para sessão de compra~~ — feito, ver `sessaocompra`. Variação: papel a mais `timeout` (`TimeoutTask` com `@Profile("timeout")`); o papel `sagas` veio depois, com a SAGA estendida (item acima). De quebra, corrigiu o pacote `com.derso.arquitetura.timeout`/`com.derso.treinohotel.timeout` (nome legado) pra `com.derso.arquitetura.sessaocompra.timeout`, consistente com o resto do domínio.

### Verificação manual da coreografia — feita em 2026-08-02 e 2026-08-08

- [x] ~~Repetir o teste manual de ida-e-volta com as três pontas vivas~~ — feito. Subimos `reservas-interno-{hotel,voo}-sagas` e `pagamento-interno-{web,sagas}`, publicamos `{"tipo":1}` direto na fila `hotel` via management UI do RabbitMQ: consumida em `hotel`, repassada e consumida em `voo` — ida confirmada, filas vazias no final (nada ficou parado).
- [x] ~~Compensação retroativa com conteúdo de mensagem preservado ainda não foi validada rodando de verdade.~~ — validado em 2026-08-08 com a amarração dummy (handlers logando + falha simulada no fim de cadeia): `docker-compose up` de `pagamento-interno` (`web`+`sagas`) e `reservas-interno` (`hotel`+`voo` no papel `sagas`), `curl -X POST /webhook`. Log confirmou a cadeia completa com o **mesmo `rastreio`** do início ao fim: webhook → `[pagamento] confirmando cobrança` → `[hotel] confirmando reserva` → `[voo] confirmando reserva` (falha simulada, sem `proximafila`) → `[hotel] cancelando reserva` → `[pagamento] ESTORNANDO pagamento`. Filas `pagamento`/`hotel`/`voo` vazias no final; `errors` com 1 mensagem — é a mensagem original que falhou em `voo` sendo dead-lettered pelo `basicNack` sem requeue (esperado: a compensação em si é uma mensagem nova publicada em `hotel`, não a mesma sendo "resgatada" do dead-letter).
  - **De quebra, achou um gap de infra:** `pagamento-interno-web` agora abre conexão RabbitMQ no boot (por causa da publicação do webhook) mas só tinha `depends_on: db` no `docker-compose.yml` — subia antes do `broker` estar pronto e caía com `Connection refused`. Corrigido adicionando `depends_on: broker (condition: service_healthy)`, no mesmo padrão já usado pelos papéis `sagas`.

### Decisões em aberto (não são bugs, são pontos a revisitar)

- `basicQos(1)` limita cada instância de SAGA a processar uma mensagem por vez — teto de throughput conhecido, revisitar se/quando houver medição de carga real.
- `JWT_PUBLIC_KEY`/`JWT_PRIVATE_KEY` no `.env`/`.env.clientes` não seguem o padrão `<SERVIÇO>_ID`/`_SECRET` do resto do arquivo (são infra compartilhada tipo `DB_HOST`, não credencial de um par específico) — considerar renomear pra algo tipo `CLIENTE_JWT_PUBLIC_KEY` se ficar confuso.
- ~~Defaults de `jwt.private-key`/`jwt.public-key` embutidos nos `application.yaml`~~ — removidos, as chaves agora são obrigatórias via env var.
- Sem captura/agregação centralizada de log — cada instância só loga local (SLF4J). Fora de escopo por agora; talvez um dia uma infra pra isso (sidecar → Elasticsearch, CloudWatch ou o que estiver à mão).
- **Segurança que hoje assume "só roda em localhost"** — sem desenho de deploy de produção ainda, cada item abaixo fica pendente de revisão isolada quando esse desenho começar:
  - JDWP (`JAVA_TOOL_OPTIONS=-agentlib:jdwp=...,address=*:PORT`) exposto e publicado no host em todo serviço, pra attach de debugger — JDWP não tem autenticação (debugger anexado = RCE). Aceitável hoje (dev local); não pode existir fora de `localhost`.
  - Secrets vivem em `.env`/`.env.<serviço>` sem gestão real (vault, secrets manager de nuvem) — aceitável hoje porque são credenciais de dev descartáveis (ver item de hygiene acima).
  - Nenhuma comunicação usa TLS (front↔`clientes`/`sessaocompra`, serviço↔serviço, app↔Postgres/RabbitMQ) — tudo HTTP/AMQP puro na rede Docker local.
  - Sem rate limit/lockout em `/login` (`clientes`) — decisão consciente de deixar como responsabilidade de borda (gateway/WAF), não da aplicação: throttling por IP não precisa entender o payload, é mais barato bloqueado antes da app, e ajusta sem redeploy. Reavaliar se algum dia precisar de lockout por `email` (semântica de negócio que a borda não enxerga sozinha).

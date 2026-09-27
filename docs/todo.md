# Pendências

Lista detalhada do que falta. O [README.md](../README.md) só resume em que pé está cada módulo e aponta pra cá.

## Features por domínio

Como `sessaocompra` se conecta com os módulos abaixo (pré-reserva, pagamento, SAGA) está desenhado em [purchase-flow-design.md](purchase-flow-design.md).

* [ ] **Timeout:**
  * [ ] sessão sem reservas completas (`TimeoutTask`, já agendado): liberar as pré-reservas diretamente via REST em `reservas-interno` — não é uma sequência SAGAS, a SAGA só começa depois que o pagamento já foi confirmado
  * [ ] pagamento iniciado sem confirmação (`TimeoutPagamentoTask` + coluna de timestamp do início do pagamento) — ver [purchase-flow-design.md](purchase-flow-design.md#dois-timeouts)

* [ ] **Pagamentos**:
  * [ ] **web:** aciona o serviço externo, webhook de confirmação e erro
    * [X] chama endpoints do serviço externo (`/efetuar`)
    * [X] webhook sucesso: publica `EXECUTE` na fila `pagamento` (dispara a SAGA de confirmação) — mensagem ainda sem ids, ver "Payload da mensagem da SAGA" em [Bloqueadores](#bloqueadores-para-a-saga-funcionar-ponta-a-ponta)
    * [ ] webhook falha: publica `DESFACA` direto na fila `sessaocompra` (nada a desfazer em hotel/voo/pagamento ainda)
  * [ ] **sagas:** eventos de confirmação e cancelamento
    * [X] recebe do anterior e passa para o próximo (filas de "entrada" e saída)
    * [ ] início da cadeia (sem fila anterior própria) — só existe pra escutar compensação voltando de hotel/voo e repassar o estorno até `sessaocompra`
  * [ ] **externo:** simula serviço externo, **introduz erros aleatórios**
    * [ ] endpoint de pagamento (`/efetuar`), _deve falhar às vezes de propósito_
    * [ ] endpoint de estorno, _deve falhar às vezes de propósito_
  * [ ] Testes integrados
    * [ ] Requisição > Externo > Webhook
    * [ ] Encaminha sucesso para outro serviço
    * [ ] Notificação de falha por outro serviço

* [ ] **Hotel:**
  * [X] **web:** interação com usuário (pré-reservas) — chamado por `sessaocompra`, não o inverso
    * [X] chama endpoints do serviço externo
  * [ ] **sagas:** eventos de confirmação e cancelamento
    * [X] recebe do anterior e passa para o próximo (filas de "entrada" e saída)
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
  * [ ] **sagas:** eventos de confirmação e cancelamento por timeout
    * [X] recebe do anterior e passa para o próximo (filas de "entrada" e saída)
    * [ ] chama endpoints do serviço externo
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

### Bloqueadores para a SAGA funcionar ponta a ponta

- [ ] **Handler de negócio da SAGA continua sem persistência real.** `ReservasSagas`/`PagamentoSagas` agora logam a mensagem (com `rastreio`), repassam adiante em sucesso e — só na ponta final da cadeia (sem `proximafila`) — lançam exceção simulando falha, disparando a compensação de verdade até `pagamento`. Isso amarra a coreografia ponta-a-ponta com regra **dummy**, mas nenhuma reserva/pagamento é confirmada/cancelada no banco ainda: falta chamar `ReservasService`/repositório de fato. Ver [saga-choreography.md](saga-choreography.md).
- [x] ~~Módulo `pagamento-interno-sagas` não existe.~~ — existe agora como papel `sagas` de `pagamento-interno` (`PagamentoSagas`, `estafila: pagamento`, `proximafila: hotel`).
- [x] ~~Webhook de pagamento é um método vazio.~~ — `PagamentoInternoController.webhookServicoExterno()` (`pagamento-interno`, profile `web`) agora gera um `rastreio` (UUID) e publica a primeira mensagem da SAGA na fila `pagamento`. **Ainda falta:** validação de assinatura/origem e proteção contra reprocessamento (é acionado por callback externo — maior risco de bug de segurança/idempotência do projeto) — isso não foi implementado, só o disparo.
- [x] ~~`sessaocompra` ainda não ativa o serviço de pagamento.~~ — `SessaoCompraController.iniciarPagamento` → `SessaoCompraService.iniciarPagamento` agora chama `pagamento-interno` (`PagamentoInternoClient`) depois da transição de status, fora de transação. Ver itens 1-2 abaixo.
- [x] ~~`reservas-interno` não tem endpoint de troca de pré-reserva.~~ — `PUT /reservas/{id}/trocar`, ver [purchase-flow-design.md](purchase-flow-design.md).
- [ ] **Ordem de start-up entre `sessaocompra-web` e `reservas-interno-{hotel,voo}-web` não está garantida no `docker-compose.yml`.** `sessaocompra` agora chama `reservas-interno` via HTTP; falta `depends_on` (checar se introduz ciclo com os serviços já existentes) — por ora assume-se que sobem saudáveis antes da primeira chamada.
- [ ] **Sessão presa em `CRIANDO_PAGAMENTO` não tem saída.** Se o processo cair entre `iniciarPagamento` e `pagamentoCriado`/`reverterPagamento` (`SessaoCompraService`), nenhum timeout pega esse estado — `TimeoutTask` só olha `INICIADA`. Falta decidir o destino (voltar pra `INICIADA`? cancelar?) e se `pagamento-interno` chegou a criar a linha em `pagamentos` nesse meio-tempo.
- [x] ~~Sem id de correlação na mensagem da SAGA.~~ — campo `rastreio` (UUID gerado no webhook) agora viaja em toda a mensagem e é logado em cada elo. **Nuance:** é só um id opaco de rastreio de fluxo, ainda não é o id da `SessaoCompra`/reserva.
- [ ] **Payload da mensagem da SAGA — correlação completa.** `ReservasSagas.idReservaDaMensagem` já lança `UnsupportedOperationException` de propósito esperando isso. Desenho fechado em [purchase-flow-design.md](purchase-flow-design.md#payload-da-mensagem-da-saga) — só ids internos (`idSessaoCompra`, `idPagamento`, `idReservaHotel`, `idReservaVooIda`/`idReservaVooVolta`) viajam na mensagem; `idExterno` nunca sai de `reservas-interno`/`pagamento-interno` — cada instância `sagas` já busca o seu localmente (`findById`, mesmo banco do papel `web` do domínio, não é chamada de rede). Quebra em:
  Ordem = sequência real: efetuar pagamento vem antes do webhook (é o que faz ele existir pra ser chamado depois); webhook é o início da cadeia SAGA; o resto já está implementado esperando os campos chegarem.
  1. [x] ~~Única chamada nova: `sessaocompra` → `pagamento-interno`~~ — `PagamentoInternoClient`/`POST /pagamentos`, chamada de `iniciarPagamento` com `idSessaoCompra` + os 3 `idReserva*`. Colunas de correlação novas em `pagamentos` (migration `V2`). De quebra, corrigido um bug pré-existente de config: `pagamento-interno/application.yaml` tinha os pares client-id/secret de `external-backend`/`internal-backend` trocados entre si (daria 401 nas duas pontas assim que uma chamada de verdade fosse feita).
  2. [x] ~~`PagamentoExternoService` vazia~~ — chama `POST /efetuar` de `pagamento-externo` de verdade (`RestClient`, mesmo padrão de `ReservasExternoService`). `PagamentoService.criarPagamento` chama fora de transação, salva a linha de `pagamentos` já completa (`id` própria + `id_externo = idTransacao` + o que veio no item 1). **Nota:** `metodo`/`valor` do `/efetuar` ainda são placeholder fixo — regra de precificação não existe.
  3. [ ] `pagamento-externo`: `WebhookRequestDTO` só carrega `status` hoje — falta `idTransacao` (`WebhookService.enviarResposta` já tem o valor, só não repassa) pra `pagamento-interno` conseguir achar a linha certa quando o callback chegar.
  4. [ ] `pagamento-interno.webhookServicoExterno()`: recebe `idTransacao`+`status` (hoje não recebe nada), busca `pagamentos WHERE id_externo = idTransacao` (coluna já existe), monta a mensagem com **`idPagamento` = a PK da linha** (não `idTransacao` — esse só serve pra achar a linha, nunca viaja na mensagem) + `idSessaoCompra` + os 3 `idReserva*`, publica. É aqui que a cadeia SAGA de fato começa.
  5. [ ] `ReservasSagas.idReservaDaMensagem`/`PagamentoSagas`: trocam o `throw`/stub por leitura direta do campo certo da mensagem (conforme profile) — o `findById` local já está implementado em `ReservasSagas`, só falta o campo chegar.
  6. [ ] Handler de `voo` agindo em duas reservas (ida+volta) a partir de uma mensagem só — forma ainda não desenhada, ver nota em purchase-flow-design.md.

### Dual-write pagamento/reservas (outbox por serviço descartado — ver desenho)

- [ ] **Avaliar Outbox no webhook de pagamento.** Confirmar pagamento no banco + publicar a 1ª mensagem da SAGA é um dual-write clássico. Decidir Outbox vs. `@Transactional` + retry quando o webhook for implementado. **Ordem:** depois do item de reservas abaixo — esse expõe a versão geral do problema (dual-write contra sistema externo não-idempotente), o outbox do webhook é um caso mais estreito do mesmo tema. **Nota:** não confundir com `sessaocompra.iniciarPagamento` → `pagamento-interno` (já implementado) — ali não há dual-write, porque a linha em `pagamentos` só é salva depois do `/efetuar` responder; falha nessa chamada só reverte o status local de `SessaoCompra` (`reverterPagamento`) e devolve erro pro front-end tentar de novo.
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

- [x] ~~Cobertura ~zero (só `contextLoads`)~~ — testes de microsserviço em todos os módulos web + integrados de contrato interno↔externo (`ReservasExternoServiceIntegrationTest`, `PagamentoExternoServiceIntegrationTest`).
- [x] ~~Testes automatizados para `web-base`~~ — achados e o que ainda falta (design de `iss`/`aud`/`kid`, refactor do wiring): [web-base-hardening.md](web-base-hardening.md).
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
- [ ] Sem reconexão automática de `Connection`/`Channel` do RabbitMQ em `sagas-common` — uma queda de broker provavelmente exige restart manual da instância consumidora (não há listener de shutdown/retry).
- [x] ~~Flyway rodando em todos os papéis, não só `web`~~ — regressão introduzida pela unificação dos módulos: antes, `-sagas`/`-timeout` nem tinham Flyway como dependência (só `-web` migrava); ao virar um artefato só, o pom passou a trazer Flyway incondicionalmente pra qualquer profile. Isso só virou bug visível em `sessaocompra-timeout` (pool `maximum-pool-size: 1` herdado do módulo antigo — Flyway precisa de 2 conexões simultâneas pra coordenação de lock durante a migration, e travava contra si mesmo até estourar timeout de 30s); em `reservas-interno`/`pagamento-interno` o pool nunca foi reduzido pro papel `sagas` (ficou em 10), então a mesma corrida nunca chegou a falhar — mas o problema de fundo (múltiplas instâncias tentando migrar ao mesmo tempo) existia igual, só mascarado. Corrigido com `spring.flyway.enabled: false` explícito nos profiles `sagas`/`timeout` dos três domínios, restaurando "só uma instância migra" como já era antes do refactor.

### Refactor planejado (sessão futura dedicada)

- [x] ~~Unificar `-common`/`-web`/`-sagas` de reservas num único artefato~~ — feito, ver `reservas-interno` e [deploy-roles-by-profile.md](deploy-roles-by-profile.md).
- [x] ~~Fazer o mesmo para pagamento~~ — feito, ver `pagamento-interno`. O papel `sagas` foi criado do zero (nunca existira como módulo). Mecanismo documentado em [deploy-roles-by-profile.md](deploy-roles-by-profile.md).
- [x] ~~Fazer o mesmo para sessão de compra~~ — feito, ver `sessaocompra`. Variação: não tem papel `sagas` (não participa da coreografia), o segundo papel é `timeout` (`TimeoutTask` com `@Profile("timeout")`), sem depender de `sagas-common`. De quebra, corrigiu o pacote `com.derso.arquitetura.timeout`/`com.derso.treinohotel.timeout` (nome legado) pra `com.derso.arquitetura.sessaocompra.timeout`, consistente com o resto do domínio.

### Verificação manual da coreografia — feita em 2026-08-02 e 2026-08-08

- [x] ~~Repetir o teste manual de ida-e-volta com as três pontas vivas~~ — feito. Subimos `reservas-interno-{hotel,voo}-sagas` e `pagamento-interno-{web,sagas}`, publicamos `{"tipo":1}` direto na fila `hotel` via management UI do RabbitMQ: consumida em `hotel`, repassada e consumida em `voo` — ida confirmada, filas vazias no final (nada ficou parado).
- [x] ~~Compensação retroativa com conteúdo de mensagem preservado ainda não foi validada rodando de verdade.~~ — validado em 2026-08-08 com a amarração dummy (handlers logando + falha simulada no fim de cadeia): `docker-compose up` de `pagamento-interno` (`web`+`sagas`) e `reservas-interno` (`hotel`+`voo` no papel `sagas`), `curl -X POST /webhook`. Log confirmou a cadeia completa com o **mesmo `rastreio`** do início ao fim: webhook → `[pagamento] confirmando cobrança` → `[hotel] confirmando reserva` → `[voo] confirmando reserva` (falha simulada, sem `proximafila`) → `[hotel] cancelando reserva` → `[pagamento] ESTORNANDO pagamento`. Filas `pagamento`/`hotel`/`voo` vazias no final; `errors` com 1 mensagem — é a mensagem original que falhou em `voo` sendo dead-lettered pelo `basicNack` sem requeue (esperado: a compensação em si é uma mensagem nova publicada em `hotel`, não a mesma sendo "resgatada" do dead-letter).
  - **De quebra, achou um gap de infra:** `pagamento-interno-web` agora abre conexão RabbitMQ no boot (por causa da publicação do webhook) mas só tinha `depends_on: db` no `docker-compose.yml` — subia antes do `broker` estar pronto e caía com `Connection refused`. Corrigido adicionando `depends_on: broker (condition: service_healthy)`, no mesmo padrão já usado pelos papéis `sagas`.

### Decisões em aberto (não são bugs, são pontos a revisitar)

- `basicQos(1)` limita cada instância de SAGA a processar uma mensagem por vez — teto de throughput conhecido, revisitar se/quando houver medição de carga real.
- `JWT_PUBLIC_KEY`/`JWT_PRIVATE_KEY` no `.env`/`.env.clientes` não seguem o padrão `<SERVIÇO>_ID`/`_SECRET` do resto do arquivo (são infra compartilhada tipo `DB_HOST`, não credencial de um par específico) — considerar renomear pra algo tipo `CLIENTE_JWT_PUBLIC_KEY` se ficar confuso.
- ~~Defaults de `jwt.private-key`/`jwt.public-key` embutidos nos `application.yaml`~~ — removidos, as chaves agora são obrigatórias via env var.
- **Segurança que hoje assume "só roda em localhost"** — sem desenho de deploy de produção ainda, cada item abaixo fica pendente de revisão isolada quando esse desenho começar:
  - JDWP (`JAVA_TOOL_OPTIONS=-agentlib:jdwp=...,address=*:PORT`) exposto e publicado no host em todo serviço, pra attach de debugger — JDWP não tem autenticação (debugger anexado = RCE). Aceitável hoje (dev local); não pode existir fora de `localhost`.
  - Secrets vivem em `.env`/`.env.<serviço>` sem gestão real (vault, secrets manager de nuvem) — aceitável hoje porque são credenciais de dev descartáveis (ver item de hygiene acima).
  - Nenhuma comunicação usa TLS (front↔`clientes`/`sessaocompra`, serviço↔serviço, app↔Postgres/RabbitMQ) — tudo HTTP/AMQP puro na rede Docker local.
  - Sem rate limit/lockout em `/login` (`clientes`) — decisão consciente de deixar como responsabilidade de borda (gateway/WAF), não da aplicação: throttling por IP não precisa entender o payload, é mais barato bloqueado antes da app, e ajusta sem redeploy. Reavaliar se algum dia precisar de lockout por `email` (semântica de negócio que a borda não enxerga sozinha).

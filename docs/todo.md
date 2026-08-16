# Pendências (lacunas técnicas de arquitetura)

Este arquivo complementa o checklist de features do [README.md](../README.md) (que já cobre o que falta por domínio: pagamento/hotel/voo). Aqui ficam as lacunas encontradas numa revisão de arquitetura em 2026-08-01 — coisas que não aparecem no checklist de feature porque são transversais ou de "amarração", não de regra de negócio de um domínio específico.

## Bloqueadores para a SAGA funcionar ponta a ponta

- [ ] **Handler de negócio da SAGA continua sem persistência real.** `ReservasSagas`/`PagamentoSagas` agora logam a mensagem (com `rastreio`), repassam adiante em sucesso e — só na ponta final da cadeia (sem `proximafila`) — lançam exceção simulando falha, disparando a compensação de verdade até `pagamento`. Isso amarra a coreografia ponta-a-ponta com regra **dummy**, mas nenhuma reserva/pagamento é confirmada/cancelada no banco ainda: falta chamar `ReservasService`/repositório de fato. Ver [saga-choreography.md](saga-choreography.md).
- [x] ~~Módulo `pagamento-interno-sagas` não existe.~~ — existe agora como papel `sagas` de `pagamento-interno` (`PagamentoSagas`, `estafila: pagamento`, `proximafila: hotel`).
- [x] ~~Webhook de pagamento é um método vazio.~~ — `PagamentoInternoController.webhookServicoExterno()` (`pagamento-interno`, profile `web`) agora gera um `rastreio` (UUID) e publica a primeira mensagem da SAGA na fila `pagamento`. **Ainda falta:** validação de assinatura/origem e proteção contra reprocessamento (é acionado por callback externo — maior risco de bug de segurança/idempotência do projeto) — isso não foi implementado, só o disparo.
- [ ] **`sessaocompra` ainda não ativa o serviço de pagamento.** Fluxo de escolha/ownership implementado (ver [purchase-flow-design.md](purchase-flow-design.md)); falta o `// TODO ativar serviço de pagamento` em `SessaoCompraController.iniciarPagamento` — é a SAGA estendida, ainda desenho.
- [x] ~~`reservas-interno` não tem endpoint de troca de pré-reserva.~~ — `PUT /reservas/{id}/trocar`, ver [purchase-flow-design.md](purchase-flow-design.md).
- [ ] **Ordem de start-up entre `sessaocompra-web` e `reservas-interno-{hotel,voo}-web` não está garantida no `docker-compose.yml`.** `sessaocompra` agora chama `reservas-interno` via HTTP; falta `depends_on` (checar se introduz ciclo com os serviços já existentes) — por ora assume-se que sobem saudáveis antes da primeira chamada.
- [x] ~~Sem id de correlação na mensagem da SAGA.~~ — campo `rastreio` (UUID gerado no webhook) agora viaja em toda a mensagem e é logado em cada elo. **Nuance:** é só um id opaco de rastreio de fluxo, ainda não é o id da `SessaoCompra`/reserva — quando o negócio real for implementado, os handlers provavelmente vão precisar de mais contexto (qual sessão/reserva afetar), não só esse rastreio.

## Avaliar padrão Outbox (candidato forte: pagamento)

- [ ] **Avaliar Outbox no webhook de pagamento.** Confirmar pagamento no banco + publicar a 1ª mensagem da SAGA é um dual-write clássico. Decidir Outbox vs. `@Transactional` + retry quando o webhook for implementado.

## Testes

- [ ] Cobertura de teste é essencialmente zero: todos os arquivos `*ApplicationTests.java` são o `contextLoads()` gerado pelo Spring Boot, nada além disso (confirmado por contagem de linhas). Os testes integrados já planejados no README (Requisição → Externo → Webhook; encaminha sucesso; notifica falha) ainda não existem em nenhum domínio.
- [ ] Testes automatizados para os módulos compartilhados (`sagas-common`, `web-base`) que já funcionam — hoje só validados indiretamente via módulos consumidores.

## Hygiene / housekeeping

- [ ] `.env` está commitado no git com credenciais de dev (ver [security-and-auth.md](security-and-auth.md)). Trocar para `.env.example` versionado + `.env` real no `.gitignore`.
- [x] ~~Arquivo órfão `reservas-interno-web/Dockerfile copy`~~ — resolvido de graça pela unificação de `reservas-interno` (o diretório antigo, e o arquivo órfão junto, deixaram de existir).
- [x] ~~`pagamento-interno-web/application.yaml` tinha placeholder malformado~~ — `${FRONT_END_ID}:frontEndId}` (faltava o `:` dentro das chaves) e `FRONT_END_ID`/`FRONT_END_SECRET` não estavam no `.env`, então a aplicação não subia (placeholder não resolvido). Corrigido pra `${FRONT_END_ID:frontEndId}` na migração pra `pagamento-interno`.
- [ ] Sem reconexão automática de `Connection`/`Channel` do RabbitMQ em `sagas-common` — uma queda de broker provavelmente exige restart manual da instância consumidora (não há listener de shutdown/retry).
- [x] ~~Flyway rodando em todos os papéis, não só `web`~~ — regressão introduzida pela unificação dos módulos: antes, `-sagas`/`-timeout` nem tinham Flyway como dependência (só `-web` migrava); ao virar um artefato só, o pom passou a trazer Flyway incondicionalmente pra qualquer profile. Isso só virou bug visível em `sessaocompra-timeout` (pool `maximum-pool-size: 1` herdado do módulo antigo — Flyway precisa de 2 conexões simultâneas pra coordenação de lock durante a migration, e travava contra si mesmo até estourar timeout de 30s); em `reservas-interno`/`pagamento-interno` o pool nunca foi reduzido pro papel `sagas` (ficou em 10), então a mesma corrida nunca chegou a falhar — mas o problema de fundo (múltiplas instâncias tentando migrar ao mesmo tempo) existia igual, só mascarado. Corrigido com `spring.flyway.enabled: false` explícito nos profiles `sagas`/`timeout` dos três domínios, restaurando "só uma instância migra" como já era antes do refactor.

## Refactor planejado (sessão futura dedicada)

- [x] ~~Unificar `-common`/`-web`/`-sagas` de reservas num único artefato~~ — feito, ver `reservas-interno` e [deploy-roles-by-profile.md](deploy-roles-by-profile.md).
- [x] ~~Fazer o mesmo para pagamento~~ — feito, ver `pagamento-interno`. O papel `sagas` foi criado do zero (nunca existira como módulo). Mecanismo documentado em [deploy-roles-by-profile.md](deploy-roles-by-profile.md).
- [x] ~~Fazer o mesmo para sessão de compra~~ — feito, ver `sessaocompra`. Variação: não tem papel `sagas` (não participa da coreografia), o segundo papel é `timeout` (`TimeoutTask` com `@Profile("timeout")`), sem depender de `sagas-common`. De quebra, corrigiu o pacote `com.derso.arquitetura.timeout`/`com.derso.treinohotel.timeout` (nome legado) pra `com.derso.arquitetura.sessaocompra.timeout`, consistente com o resto do domínio.

## Verificação manual da coreografia — feita em 2026-08-02 e 2026-08-08

- [x] ~~Repetir o teste manual de ida-e-volta com as três pontas vivas~~ — feito. Subimos `reservas-interno-{hotel,voo}-sagas` e `pagamento-interno-{web,sagas}`, publicamos `{"tipo":1}` direto na fila `hotel` via management UI do RabbitMQ: consumida em `hotel`, repassada e consumida em `voo` — ida confirmada, filas vazias no final (nada ficou parado).
- [x] ~~Compensação retroativa com conteúdo de mensagem preservado ainda não foi validada rodando de verdade.~~ — validado em 2026-08-08 com a amarração dummy (handlers logando + falha simulada no fim de cadeia): `docker-compose up` de `pagamento-interno` (`web`+`sagas`) e `reservas-interno` (`hotel`+`voo` no papel `sagas`), `curl -X POST /webhook`. Log confirmou a cadeia completa com o **mesmo `rastreio`** do início ao fim: webhook → `[pagamento] confirmando cobrança` → `[hotel] confirmando reserva` → `[voo] confirmando reserva` (falha simulada, sem `proximafila`) → `[hotel] cancelando reserva` → `[pagamento] ESTORNANDO pagamento`. Filas `pagamento`/`hotel`/`voo` vazias no final; `errors` com 1 mensagem — é a mensagem original que falhou em `voo` sendo dead-lettered pelo `basicNack` sem requeue (esperado: a compensação em si é uma mensagem nova publicada em `hotel`, não a mesma sendo "resgatada" do dead-letter).
  - **De quebra, achou um gap de infra:** `pagamento-interno-web` agora abre conexão RabbitMQ no boot (por causa da publicação do webhook) mas só tinha `depends_on: db` no `docker-compose.yml` — subia antes do `broker` estar pronto e caía com `Connection refused`. Corrigido adicionando `depends_on: broker (condition: service_healthy)`, no mesmo padrão já usado pelos papéis `sagas`.

## Decisões em aberto (não são bugs, são pontos a revisitar)

- `basicQos(1)` limita cada instância de SAGA a processar uma mensagem por vez — teto de throughput conhecido, revisitar se/quando houver medição de carga real.
- `JWT_PUBLIC_KEY`/`JWT_PRIVATE_KEY` no `.env`/`.env.clientes` não seguem o padrão `<SERVIÇO>_ID`/`_SECRET` do resto do arquivo (são infra compartilhada tipo `DB_HOST`, não credencial de um par específico) — considerar renomear pra algo tipo `CLIENTE_JWT_PUBLIC_KEY` se ficar confuso.
- Defaults de `jwt.private-key`/`jwt.public-key` embutidos nos `application.yaml` (fallback pra rodar sem env var) duplicam o segredo que já está no `.env`/`.env.clientes` — avaliar remover e deixar obrigatório via env var, se não houver fluxo real de rodar sem Docker.
- Debug remoto (JDWP) não está configurado no `docker-compose.yml` — falta `JAVA_TOOL_OPTIONS=-agentlib:jdwp=...` + porta exposta nos serviços `-web` pra debugar via attach do VSCode dentro do container.

# Pendências (lacunas técnicas de arquitetura)

Este arquivo complementa o checklist de features do [README.md](../README.md) (que já cobre o que falta por domínio: pagamento/hotel/voo). Aqui ficam as lacunas encontradas numa revisão de arquitetura em 2026-08-01 — coisas que não aparecem no checklist de feature porque são transversais ou de "amarração", não de regra de negócio de um domínio específico.

## Bloqueadores para a SAGA funcionar ponta a ponta

- [ ] **Handler de negócio da SAGA é stub.** `ReservasSagas` (`reservas-interno/.../sagas/ReservasSagas.java`) só imprime a mensagem recebida. Precisa chamar `ReservasService`/repositório para confirmar ou cancelar a reserva, e lançar exceção para acionar compensação quando falhar. Ver [saga-choreography.md](saga-choreography.md).
- [x] ~~Módulo `pagamento-interno-sagas` não existe.~~ — existe agora como papel `sagas` de `pagamento-interno` (`PagamentoSagas`, `estafila: pagamento`, `proximafila: hotel`). **Continua verdade, porém:** nada publica a primeira mensagem na fila `pagamento` ainda — isso é o item abaixo (webhook), não este.
- [ ] **Webhook de pagamento é um método vazio.** `PagamentoInternoController.webhookServicoExterno()` (`pagamento-interno`, profile `web`) não faz nada. Quando implementado, precisa (a) validar assinatura/origem e proteção contra reprocessamento (é acionado por callback externo — maior risco de bug de segurança/idempotência do projeto) e (b) publicar a primeira mensagem da SAGA na fila `pagamento` — é o início de toda a cadeia.
- [ ] **`sessaocompra` (profile `web`) como árbitro ainda não está ligada aos outros serviços.** `SessaoCompraController.iniciarPagamento` e trechos de `atualizarEstadoCompra` têm TODOs explícitos para as chamadas que fariam o papel de árbitro de fato (ver comentários no próprio controller).
- [ ] **Sem id de correlação na mensagem da SAGA.** A mensagem hoje só carrega `tipo` + o que o handler colocar. Antes de implementar os handlers de negócio, vale decidir que campo identifica a sessão de compra/reserva em toda a cadeia.

## Avaliar padrão Outbox (candidato forte: pagamento)

- [ ] **Avaliar Outbox no webhook de pagamento.** Confirmar pagamento no banco + publicar a 1ª mensagem da SAGA é um dual-write clássico. Decidir Outbox vs. `@Transactional` + retry quando o webhook for implementado.

## Testes

- [ ] Cobertura de teste é essencialmente zero: todos os arquivos `*ApplicationTests.java` são o `contextLoads()` gerado pelo Spring Boot, nada além disso (confirmado por contagem de linhas). Os testes integrados já planejados no README (Requisição → Externo → Webhook; encaminha sucesso; notifica falha) ainda não existem em nenhum domínio.

## Hygiene / housekeeping

- [ ] `.env` está commitado no git com credenciais de dev (ver [security-and-auth.md](security-and-auth.md)). Trocar para `.env.example` versionado + `.env` real no `.gitignore`.
- [x] ~~Arquivo órfão `reservas-interno-web/Dockerfile copy`~~ — resolvido de graça pela unificação de `reservas-interno` (o diretório antigo, e o arquivo órfão junto, deixaram de existir).
- [x] ~~`pagamento-interno-web/application.yaml` tinha placeholder malformado~~ — `${FRONT_END_ID}:frontEndId}` (faltava o `:` dentro das chaves) e `FRONT_END_ID`/`FRONT_END_SECRET` não estavam no `.env`, então a aplicação não subia (placeholder não resolvido). Corrigido pra `${FRONT_END_ID:frontEndId}` na migração pra `pagamento-interno`.
- [ ] Sem reconexão automática de `Connection`/`Channel` do RabbitMQ em `sagas-common` — uma queda de broker provavelmente exige restart manual da instância consumidora (não há listener de shutdown/retry).
- [x] ~~Flyway rodando em todos os papéis, não só `web`~~ — regressão introduzida pela unificação dos módulos: antes, `-sagas`/`-timeout` nem tinham Flyway como dependência (só `-web` migrava); ao virar um artefato só, o pom passou a trazer Flyway incondicionalmente pra qualquer profile. Isso só virou bug visível em `sessaocompra-timeout` (pool `maximum-pool-size: 1` herdado do módulo antigo — Flyway precisa de 2 conexões simultâneas pra coordenação de lock durante a migration, e travava contra si mesmo até estourar timeout de 30s); em `reservas-interno`/`pagamento-interno` o pool nunca foi reduzido pro papel `sagas` (ficou em 10), então a mesma corrida nunca chegou a falhar — mas o problema de fundo (múltiplas instâncias tentando migrar ao mesmo tempo) existia igual, só mascarado. Corrigido com `spring.flyway.enabled: false` explícito nos profiles `sagas`/`timeout` dos três domínios, restaurando "só uma instância migra" como já era antes do refactor.

## Refactor planejado (sessão futura dedicada)

- [x] ~~Unificar `-common`/`-web`/`-sagas` de reservas num único artefato~~ — feito, ver `reservas-interno` e [deploy-roles-by-profile.md](deploy-roles-by-profile.md).
- [x] ~~Fazer o mesmo para pagamento~~ — feito, ver `pagamento-interno`. O papel `sagas` foi criado do zero (nunca existira como módulo). Decisão e mecanismo documentados em [module-boundaries.md](module-boundaries.md#3-artefato-único-com-dois-entrypoints-escaláveis-por-configuração--concluído-para-reservas-pagamento-e-sessão-de-compra) e [deploy-roles-by-profile.md](deploy-roles-by-profile.md).
- [x] ~~Fazer o mesmo para sessão de compra~~ — feito, ver `sessaocompra`. Variação: não tem papel `sagas` (não participa da coreografia), o segundo papel é `timeout` (`TimeoutTask` com `@Profile("timeout")`), sem depender de `sagas-common`. De quebra, corrigiu o pacote `com.derso.arquitetura.timeout`/`com.derso.treinohotel.timeout` (nome legado) pra `com.derso.arquitetura.sessaocompra.timeout`, consistente com o resto do domínio.

## Verificação manual da coreografia — feita em 2026-08-02, resultado parcial

- [x] ~~Repetir o teste manual de ida-e-volta com as três pontas vivas~~ — feito. Subimos `reservas-interno-{hotel,voo}-sagas` e `pagamento-interno-{web,sagas}`, publicamos `{"tipo":1}` direto na fila `hotel` via management UI do RabbitMQ: consumida em `hotel`, repassada e consumida em `voo` — ida confirmada, filas vazias no final (nada ficou parado).
- [ ] **Compensação retroativa com conteúdo de mensagem preservado ainda não foi validada.** Tentamos forçar erro publicando um corpo não-JSON direto em `voo`, esperando ver a compensação voltar até `hotel` e depois até `pagamento`. Não funcionou como esperado — e o motivo é importante: `SagasMessaging.iniciarConsumo()` só republica compensação (`tipo=DESFACA`) se a variável `mensagem` não for `null`; num corpo não-JSON, o `decode()` falha **antes** de `mensagem` ser atribuída, então não existe conteúdo pra repassar pra trás — a mensagem só cai no dead-letter (`errors`), corretamente, sem compensar. Isso não é bug, é o código se comportando certo diante de um teste que simulou a falha errada (falha de parsing, não falha de processamento).
  - O problema real: os handlers (`ReservasSagas`/`PagamentoSagas`) são stubs que só fazem `println` — nunca lançam exceção. Não existe hoje, sem alterar código, uma forma de simular "mensagem válida recebida, handler falhou processando" (que é o caminho que de fato dispara compensação com mensagem preservada).
  - Pra validar esse caminho de verdade: ou (a) implementar o handler de negócio real (item "Handler de negócio da SAGA é stub" acima), que naturalmente vai poder falhar; ou (b) numa sessão futura, forçar uma exceção temporária só pra esse teste manual e reverter antes de commitar. Adiado — não é bloqueador da unificação de módulos, é validação de comportamento que só faz sentido completa quando a lógica de negócio existir.

## Decisões em aberto (não são bugs, são pontos a revisitar)

- `basicQos(1)` limita cada instância de SAGA a processar uma mensagem por vez — teto de throughput conhecido, revisitar se/quando houver medição de carga real.

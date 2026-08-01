# Pendências (lacunas técnicas de arquitetura)

Este arquivo complementa o checklist de features do [README.md](../README.md) (que já cobre o que falta por domínio: pagamento/hotel/voo). Aqui ficam as lacunas encontradas numa revisão de arquitetura em 2026-08-01 — coisas que não aparecem no checklist de feature porque são transversais ou de "amarração", não de regra de negócio de um domínio específico.

## Bloqueadores para a SAGA funcionar ponta a ponta

- [ ] **Handler de negócio da SAGA é stub.** `ReservasSagas` (`reservas-interno/.../sagas/ReservasSagas.java`) só imprime a mensagem recebida. Precisa chamar `ReservasService`/repositório para confirmar ou cancelar a reserva, e lançar exceção para acionar compensação quando falhar. Ver [saga-choreography.md](saga-choreography.md).
- [ ] **Módulo `pagamento-interno-sagas` não existe.** Não há nada publicando a primeira mensagem na fila `pagamento` — é o início de toda a cadeia. `docker-compose.yml` já tem o comentário `# TODO serviço SAGAS de pagamento (interno)`.
- [ ] **Webhook de pagamento é um método vazio.** `PagamentoInternoController.webhookServicoExterno()` (`pagamento-interno-web`) não faz nada. Quando implementado, é o ponto de maior risco de bug de segurança/idempotência do projeto, por ser acionado por callback externo — merece validação de assinatura/origem e proteção contra reprocessamento antes de publicar na fila de SAGA.
- [ ] **`sessaocompra-web` como árbitro ainda não está ligada aos outros serviços.** `SessaoCompraController.iniciarPagamento` e trechos de `atualizarEstadoCompra` têm TODOs explícitos para as chamadas que fariam o papel de árbitro de fato (ver comentários no próprio controller).
- [ ] **Sem id de correlação na mensagem da SAGA.** A mensagem hoje só carrega `tipo` + o que o handler colocar. Antes de implementar os handlers de negócio, vale decidir que campo identifica a sessão de compra/reserva em toda a cadeia.

## Testes

- [ ] Cobertura de teste é essencialmente zero: todos os arquivos `*ApplicationTests.java` são o `contextLoads()` gerado pelo Spring Boot, nada além disso (confirmado por contagem de linhas). Os testes integrados já planejados no README (Requisição → Externo → Webhook; encaminha sucesso; notifica falha) ainda não existem em nenhum domínio.

## Hygiene / housekeeping

- [ ] `.env` está commitado no git com credenciais de dev (ver [security-and-auth.md](security-and-auth.md)). Trocar para `.env.example` versionado + `.env` real no `.gitignore`.
- [x] ~~Arquivo órfão `reservas-interno-web/Dockerfile copy`~~ — resolvido de graça pela unificação de `reservas-interno` (o diretório antigo, e o arquivo órfão junto, deixaram de existir).
- [ ] Sem reconexão automática de `Connection`/`Channel` do RabbitMQ em `sagas-common` — uma queda de broker provavelmente exige restart manual da instância consumidora (não há listener de shutdown/retry).

## Refactor planejado (sessão futura dedicada)

- [x] ~~Unificar `-common`/`-web`/`-sagas` de reservas num único artefato~~ — feito, ver `reservas-interno` e [deploy-roles-by-profile.md](deploy-roles-by-profile.md).
- [ ] **Fazer o mesmo para pagamento**: unificar `pagamento-interno-{common,web}` (e o `-sagas` que ainda não existe) num único artefato, com o papel (REST vs. consumidor de fila) escolhido por profile/config em runtime. Decisão e mecanismo documentados em [module-boundaries.md](module-boundaries.md#3-artefato-único-com-dois-entrypoints-escaláveis-por-configuração--concluído-para-reservas) e [deploy-roles-by-profile.md](deploy-roles-by-profile.md) — é um refactor grande, não um ajuste pontual, então merece sessão própria em vez de ser feito incrementalmente junto de outra tarefa.

## Decisões em aberto (não são bugs, são pontos a revisitar)

- `basicQos(1)` limita cada instância de SAGA a processar uma mensagem por vez — teto de throughput conhecido, revisitar se/quando houver medição de carga real.

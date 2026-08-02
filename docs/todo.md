# Pendências (lacunas técnicas de arquitetura)

Este arquivo complementa o checklist de features do [README.md](../README.md) (que já cobre o que falta por domínio: pagamento/hotel/voo). Aqui ficam as lacunas encontradas numa revisão de arquitetura em 2026-08-01 — coisas que não aparecem no checklist de feature porque são transversais ou de "amarração", não de regra de negócio de um domínio específico.

## Bloqueadores para a SAGA funcionar ponta a ponta

- [ ] **Handler de negócio da SAGA é stub.** `ReservasSagas` (`reservas-interno/.../sagas/ReservasSagas.java`) só imprime a mensagem recebida. Precisa chamar `ReservasService`/repositório para confirmar ou cancelar a reserva, e lançar exceção para acionar compensação quando falhar. Ver [saga-choreography.md](saga-choreography.md).
- [x] ~~Módulo `pagamento-interno-sagas` não existe.~~ — existe agora como papel `sagas` de `pagamento-interno` (`PagamentoSagas`, `estafila: pagamento`, `proximafila: hotel`). **Continua verdade, porém:** nada publica a primeira mensagem na fila `pagamento` ainda — isso é o item abaixo (webhook), não este.
- [ ] **Webhook de pagamento é um método vazio.** `PagamentoInternoController.webhookServicoExterno()` (`pagamento-interno`, profile `web`) não faz nada. Quando implementado, precisa (a) validar assinatura/origem e proteção contra reprocessamento (é acionado por callback externo — maior risco de bug de segurança/idempotência do projeto) e (b) publicar a primeira mensagem da SAGA na fila `pagamento` — é o início de toda a cadeia.
- [ ] **`sessaocompra-web` como árbitro ainda não está ligada aos outros serviços.** `SessaoCompraController.iniciarPagamento` e trechos de `atualizarEstadoCompra` têm TODOs explícitos para as chamadas que fariam o papel de árbitro de fato (ver comentários no próprio controller).
- [ ] **Sem id de correlação na mensagem da SAGA.** A mensagem hoje só carrega `tipo` + o que o handler colocar. Antes de implementar os handlers de negócio, vale decidir que campo identifica a sessão de compra/reserva em toda a cadeia.

## Testes

- [ ] Cobertura de teste é essencialmente zero: todos os arquivos `*ApplicationTests.java` são o `contextLoads()` gerado pelo Spring Boot, nada além disso (confirmado por contagem de linhas). Os testes integrados já planejados no README (Requisição → Externo → Webhook; encaminha sucesso; notifica falha) ainda não existem em nenhum domínio.

## Hygiene / housekeeping

- [ ] `.env` está commitado no git com credenciais de dev (ver [security-and-auth.md](security-and-auth.md)). Trocar para `.env.example` versionado + `.env` real no `.gitignore`.
- [x] ~~Arquivo órfão `reservas-interno-web/Dockerfile copy`~~ — resolvido de graça pela unificação de `reservas-interno` (o diretório antigo, e o arquivo órfão junto, deixaram de existir).
- [x] ~~`pagamento-interno-web/application.yaml` tinha placeholder malformado~~ — `${FRONT_END_ID}:frontEndId}` (faltava o `:` dentro das chaves) e `FRONT_END_ID`/`FRONT_END_SECRET` não estavam no `.env`, então a aplicação não subia (placeholder não resolvido). Corrigido pra `${FRONT_END_ID:frontEndId}` na migração pra `pagamento-interno`.
- [ ] Sem reconexão automática de `Connection`/`Channel` do RabbitMQ em `sagas-common` — uma queda de broker provavelmente exige restart manual da instância consumidora (não há listener de shutdown/retry).

## Refactor planejado (sessão futura dedicada)

- [x] ~~Unificar `-common`/`-web`/`-sagas` de reservas num único artefato~~ — feito, ver `reservas-interno` e [deploy-roles-by-profile.md](deploy-roles-by-profile.md).
- [x] ~~Fazer o mesmo para pagamento~~ — feito, ver `pagamento-interno`. O papel `sagas` foi criado do zero (nunca existira como módulo). Decisão e mecanismo documentados em [module-boundaries.md](module-boundaries.md#3-artefato-único-com-dois-entrypoints-escaláveis-por-configuração--concluído-para-reservas-e-pagamento) e [deploy-roles-by-profile.md](deploy-roles-by-profile.md).

## Verificação manual pendente (fazer quando reservas-interno e pagamento-interno estiverem os dois rodando com papel `sagas` ativo)

- [ ] **Repetir o teste manual de ida-e-volta da coreografia, agora com as três pontas vivas.** Antes desta sessão de refactor, só existiam consumidores reais em `hotel` e `voo` (`pagamento-interno-sagas` nem existia) — publicar uma mensagem direto na fila `hotel`, deixá-la seguir até `voo`, simular erro lá (ex.: corpo não-JSON, que `SagasMessaging.decode()` rejeita com `IllegalArgumentException`) e observar a compensação voltando: `voo` (nack + `DESFACA` pra `hotel`) → `hotel` (consome, repassa `DESFACA` pra `pagamento`) → **antes**, a mensagem morria ali, sem ninguém ouvindo `pagamento`. Agora que o papel `sagas` existe em `pagamento-interno`, o teste é: confirmar que essa mensagem final é de fato **consumida** em `pagamento` (aparece no log stub `Recebida mensagem: ...`), não só publicada/perdida.
  - Passos: subir `db`, `broker`, `reservas-interno-{hotel,voo}-{web,sagas}` e `pagamento-interno-{web,sagas}` via `docker-compose up`; usar o management UI do RabbitMQ (porta `${BROKER_PORT_WEB}`) pra publicar a mensagem inicial direto na fila `hotel` (`{"tipo":1}` ou corpo vazio, já que `tipo` default é `EXECUTE`); depois publicar um corpo inválido direto na fila `voo` pra forçar a falha.
  - Esperado: 3 filas (`pagamento`, `hotel`, `voo`) e agora **3 consumidores reais** (não só 3 filas declaradas com 2 consumidores, como antes do refactor) — logs de `Recebida mensagem` devem aparecer nas 3 instâncias `-sagas` ao longo do percurso de ida e de volta.
  - Ainda vai ser só mecânica de fila, não regra de negócio (os handlers continuam stub) — o objetivo aqui é confirmar que o "buraco" em `pagamento` foi fechado, não validar confirmação/estorno real.

## Decisões em aberto (não são bugs, são pontos a revisitar)

- `basicQos(1)` limita cada instância de SAGA a processar uma mensagem por vez — teto de throughput conhecido, revisitar se/quando houver medição de carga real.

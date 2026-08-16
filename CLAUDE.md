# Estudo Arquitetura — Agência de Viagens (Monorepo)

Monorepo de estudo simulando uma agência de viagens com transação distribuída entre passagens aéreas, hotel e pagamentos. O foco é praticar arquitetura (SAGA por coreografia, multi-módulo Maven, unidades de deploy separadas) — a regra de negócio real de cada domínio ainda está, em boa parte, por implementar. Documentação detalhada em [docs/](docs/README.md), indexada por tópico — carregue sob demanda.

## Stack

Java 25 (virtual threads), Spring Boot 4.0.1, Maven multi-módulo (8 módulos + parent POM), Postgres 18.1 (1 database por bounded context) + Flyway, RabbitMQ 4.2.2 (cliente Java cru), Docker Compose. Rodar local: `docker-compose up` (usa `.env` na raiz).

## Módulos

`clientes` · `sessaocompra` (roda 2x por papel via profile `web`/`timeout`) · `reservas-{interno,externo}` (cada um roda 2x via profile `hotel`/`voo`; `reservas-interno` roda ainda 2x por papel via profile `web`/`sagas`) · `pagamento-{interno,externo}` (`pagamento-interno` roda 2x por papel via profile `web`/`sagas`, sem eixo de domínio) · `web-base` e `sagas-common` (bibliotecas transversais). Mapa completo, portas e diagrama de fluxo: [docs/architecture-overview.md](docs/architecture-overview.md).

## Decisões arquiteturais

- **`reservas-interno`, `pagamento-interno` e `sessaocompra` são cada um um artefato único**, com controller REST e listener de fila no mesmo processo — o papel ativo em cada instância é escolhido por profile Spring em runtime (`@Profile("web")`/`@Profile("sagas")`/`@Profile("timeout")`). Isso dá escala independente entre entrypoint REST e entrypoint de fila (N instâncias web, M instâncias fila) sem o custo de coordenar módulos Maven separados por domínio. `sessaocompra` não participa da SAGA — seu segundo papel é `timeout` (job `@Scheduled`), não `sagas`. Mecanismo em [docs/deploy-roles-by-profile.md](docs/deploy-roles-by-profile.md), organização interna da regra de negócio em [docs/module-boundaries.md](docs/module-boundaries.md). **Isso é verdade hoje, mas há um desenho (não implementado) que estende a coreografia até `sessaocompra`** — ver [docs/purchase-flow-design.md](docs/purchase-flow-design.md).
- **SAGA por coreografia, não orquestração central.** Cada serviço só conhece a fila anterior/próxima; erro no handler dispara republish automático de compensação (`tipo=DESFACA`) retroativo na cadeia — mecânica implementada em `sagas-common`, cadeia atual é `pagamento → hotel → voo`. Detalhe: [docs/saga-choreography.md](docs/saga-choreography.md).
- **Database por bounded context**, mesmo quando `-web` e `-sagas` do mesmo domínio compartilham banco (são a mesma unidade lógica de negócio, só split por entrypoint/escala).
- **Duas identidades de autenticação**: JWT para cliente final, client-id/secret por par de serviços internos. Detalhe e limitações conhecidas: [docs/security-and-auth.md](docs/security-and-auth.md).
- **Chaos engineering nos simuladores `-externo`**: falha e latência aleatórias propositais (`CHANCE_FALHA`), para exercitar os caminhos de compensação da SAGA.

## Convenções de escrita (código e docs)

- Comentário no código: só o que não é óbvio lendo o código (armadilha, invariante, motivo de workaround) — curto, de preferência uma linha
  - se o porquê já está em `docs/`, aponta pra lá em vez de reexplicar
- Documentação em `docs/`:
  - desenho ainda não implementado: detalhe completo (é a única fonte de verdade nesse momento)
  - depois de implementado: código vira fonte de verdade do *como*; a doc encolhe pro *porquê* (decisão de negócio/projeto, armadilhas encontradas)
  - cada fato mora num lugar só — duplicar entre código e doc(s) tende a ficar desatualizado
- Escrita em geral (docs, TODOs, mensagens): bullets aninhados e frases curtas em vez de parágrafo denso

## Pendências

A mecânica de infraestrutura (filas, auth, config) está mais madura que a regra de negócio que deveria carregar. Destaques: handlers da SAGA ainda são stub (só logam), webhook de pagamento é método vazio (nem confirma pagamento nem publica a primeira mensagem da SAGA), cobertura de teste é ~zero. Lista completa e categorizada: [docs/todo.md](docs/todo.md). Checklist de features por domínio (hotel/voo/pagamento): [README.md](README.md).

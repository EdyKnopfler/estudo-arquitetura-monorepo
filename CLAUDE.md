# Estudo Arquitetura — Agência de Viagens (Monorepo)

Monorepo de estudo simulando uma agência de viagens com transação distribuída entre passagens aéreas, hotel e pagamentos. O foco é praticar arquitetura (SAGA por coreografia, multi-módulo Maven, unidades de deploy separadas) — a regra de negócio real de cada domínio ainda está, em boa parte, por implementar. Documentação detalhada em [docs/](docs/README.md), indexada por tópico — carregue sob demanda.

## Stack

Java 25 (virtual threads), Spring Boot 4.0.1, Maven multi-módulo (10 módulos + parent POM), Postgres 18.1 (1 database por bounded context) + Flyway, RabbitMQ 4.2.2 (cliente Java cru), Docker Compose. Rodar local: `docker-compose up` (usa `.env` na raiz).

## Módulos

`clientes` · `sessaocompra-{common,web,timeout}` · `reservas-{interno,externo}` (cada um roda 2x via profile `hotel`/`voo`; `reservas-interno` roda ainda 2x por papel via profile `web`/`sagas`) · `pagamento-{interno,externo}` (`pagamento-interno` roda 2x por papel via profile `web`/`sagas`, sem eixo de domínio) · `web-base` e `sagas-common` (bibliotecas transversais). Mapa completo, portas e diagrama de fluxo: [docs/architecture-overview.md](docs/architecture-overview.md).

## Decisões arquiteturais

- **`reservas-interno` e `pagamento-interno` já unificam `-common`/`-web`/`-sagas` num artefato só cada**, com o papel (web vs. fila) escolhido por profile Spring em runtime (`@Profile("web")`/`@Profile("sagas")`) — a independência de deploy que a separação em três módulos prometia só valia para mudanças que não tocam o `-common`, então o ganho real (escalar contagem de instância) foi preservado sem o custo de coordenar três módulos. Pra pagamento, o papel `sagas` foi criado do zero (nunca existira como módulo). Raciocínio completo em [docs/module-boundaries.md](docs/module-boundaries.md), mecanismo em [docs/deploy-roles-by-profile.md](docs/deploy-roles-by-profile.md).
- **SAGA por coreografia, não orquestração central.** Cada serviço só conhece a fila anterior/próxima; erro no handler dispara republish automático de compensação (`tipo=DESFACA`) retroativo na cadeia — mecânica implementada em `sagas-common`, cadeia atual é `pagamento → hotel → voo`. Detalhe: [docs/saga-choreography.md](docs/saga-choreography.md).
- **Database por bounded context**, mesmo quando `-web` e `-sagas` do mesmo domínio compartilham banco (são a mesma unidade lógica de negócio, só split por entrypoint/escala).
- **Duas identidades de autenticação**: JWT para cliente final, client-id/secret por par de serviços internos. Detalhe e limitações conhecidas: [docs/security-and-auth.md](docs/security-and-auth.md).
- **Chaos engineering nos simuladores `-externo`**: falha e latência aleatórias propositais (`CHANCE_FALHA`), para exercitar os caminhos de compensação da SAGA.

## Pendências

A mecânica de infraestrutura (filas, auth, config) está mais madura que a regra de negócio que deveria carregar. Destaques: handlers da SAGA ainda são stub (só logam), webhook de pagamento é método vazio (nem confirma pagamento nem publica a primeira mensagem da SAGA), cobertura de teste é ~zero. Lista completa e categorizada: [docs/todo.md](docs/todo.md). Checklist de features por domínio (hotel/voo/pagamento): [README.md](README.md).

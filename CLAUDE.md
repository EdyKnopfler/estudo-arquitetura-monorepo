# Estudo Arquitetura — Agência de Viagens (Monorepo)

Monorepo de estudo simulando uma agência de viagens com transação distribuída entre passagens aéreas, hotel e pagamentos. O foco é praticar arquitetura (SAGA por coreografia, multi-módulo Maven, unidades de deploy separadas) — a regra de negócio real de cada domínio ainda está, em boa parte, por implementar. Documentação detalhada em [docs/](docs/README.md), indexada por tópico — carregue sob demanda.

## Stack

Java 25 (virtual threads), Spring Boot 4.0.1, Maven multi-módulo (13 módulos + parent POM), Postgres 18.1 (1 database por bounded context) + Flyway, RabbitMQ 4.2.2 (cliente Java cru), Docker Compose. Rodar local: `docker-compose up` (usa `.env` na raiz).

## Módulos

`clientes` · `sessaocompra-{common,web,timeout}` · `reservas-{interno-common,interno-web,interno-sagas,externo}` (cada um roda 2x via profile `hotel`/`voo`) · `pagamento-{interno-common,interno-web,externo}` · `web-base` e `sagas-common` (bibliotecas transversais). Mapa completo, portas e diagrama de fluxo: [docs/architecture-overview.md](docs/architecture-overview.md).

## Decisões arquiteturais

- **Estado atual: regra de negócio em bibliotecas `-common`, consumidas por unidades de deploy separadas (`-web` REST e `-sagas` fila).** **Refactor planejado (sessão futura dedicada):** unificar `-common`/`-web`/`-sagas` de cada domínio num artefato só, com o papel (web vs. fila) escolhido por profile/config em runtime — a independência de deploy que a separação atual promete só vale para mudanças que não tocam o `-common`, então o ganho real (escalar contagem de instância) é preservado sem o custo de coordenar três módulos. Raciocínio completo em [docs/module-boundaries.md](docs/module-boundaries.md).
- **SAGA por coreografia, não orquestração central.** Cada serviço só conhece a fila anterior/próxima; erro no handler dispara republish automático de compensação (`tipo=DESFACA`) retroativo na cadeia — mecânica implementada em `sagas-common`, cadeia atual é `pagamento → hotel → voo`. Detalhe: [docs/saga-choreography.md](docs/saga-choreography.md).
- **Database por bounded context**, mesmo quando `-web` e `-sagas` do mesmo domínio compartilham banco (são a mesma unidade lógica de negócio, só split por entrypoint/escala).
- **Duas identidades de autenticação**: JWT para cliente final, client-id/secret por par de serviços internos. Detalhe e limitações conhecidas: [docs/security-and-auth.md](docs/security-and-auth.md).
- **Chaos engineering nos simuladores `-externo`**: falha e latência aleatórias propositais (`CHANCE_FALHA`), para exercitar os caminhos de compensação da SAGA.

## Pendências

A mecânica de infraestrutura (filas, auth, config) está mais madura que a regra de negócio que deveria carregar. Destaques: handlers da SAGA ainda são stub (só logam), `pagamento-interno-sagas` nem existe como módulo, webhook de pagamento é método vazio, cobertura de teste é ~zero. Lista completa e categorizada: [docs/todo.md](docs/todo.md). Checklist de features por domínio (hotel/voo/pagamento): [README.md](README.md).

# Estudo Arquitetura — Agência de Viagens (Monorepo)

Monorepo de estudo simulando uma agência de viagens com transação distribuída entre passagens aéreas, hotel e pagamentos. O foco é praticar arquitetura (SAGA por coreografia, multi-módulo Maven, unidades de deploy separadas) — a regra de negócio real de cada domínio ainda está, em boa parte, por implementar. Documentação detalhada em [docs/](docs/README.md), indexada por tópico — carregue sob demanda.

## Stack

Java 25 (virtual threads), Spring Boot 4.0.1, Maven multi-módulo (8 módulos + parent POM), Postgres 18.1 (1 database por bounded context) + Flyway, RabbitMQ 4.2.2 (cliente Java cru), Docker Compose. Rodar local: `docker-compose up` (usa `.env` na raiz).

## Módulos

`clientes` · `sessaocompra` (roda por papel via profile `web`/`sagas`/`timeout`) · `reservas-{interno,externo}` (cada um roda 2x via profile `hotel`/`voo`; `reservas-interno` roda ainda 2x por papel via profile `web`/`sagas`) · `pagamento-{interno,externo}` (`pagamento-interno` roda 2x por papel via profile `web`/`sagas`, sem eixo de domínio) · `web-base` e `sagas-common` (bibliotecas transversais). Mapa completo, portas e diagrama de fluxo: [docs/architecture-overview.md](docs/architecture-overview.md).

## Decisões arquiteturais

- **`reservas-interno`, `pagamento-interno` e `sessaocompra` são cada um um artefato único**, com controller REST e listener de fila no mesmo processo — o papel ativo em cada instância é escolhido por profile Spring em runtime (`@Profile("web")`/`@Profile("sagas")`/`@Profile("timeout")`). Isso dá escala independente entre entrypoint REST e entrypoint de fila (N instâncias web, M instâncias fila) sem o custo de coordenar módulos Maven separados por domínio. `sessaocompra` tem ainda o papel `timeout` (job `@Scheduled`). Mecanismo em [docs/deploy-roles-by-profile.md](docs/deploy-roles-by-profile.md), organização interna da regra de negócio em [docs/module-boundaries.md](docs/module-boundaries.md).
- **SAGA por coreografia, não orquestração central.** Cada serviço só conhece a fila anterior/próxima; erro no handler dispara republish automático de compensação (`tipo=DESFACA`) retroativo na cadeia — mecânica em `sagas-common`. Cadeia `pagamento → hotel → voo`, com `sessaocompra` nas duas pontas (confirma a viagem no fim do sucesso, reverte a sessão no fim da compensação) — desenho em [docs/purchase-flow-design.md](docs/purchase-flow-design.md). Detalhe: [docs/saga-choreography.md](docs/saga-choreography.md).
- **Database por bounded context**, mesmo quando `-web` e `-sagas` do mesmo domínio compartilham banco (são a mesma unidade lógica de negócio, só split por entrypoint/escala).
- **Duas identidades de autenticação**: JWT para cliente final, client-id/secret por par de serviços internos. Detalhe e limitações conhecidas: [docs/security-and-auth.md](docs/security-and-auth.md).
- **Chaos engineering nos simuladores `-externo`**: falha e latência aleatórias propositais (`CHANCE_FALHA`), para exercitar os caminhos de compensação da SAGA.

## Convenções de escrita (código e docs)

- Comentário no código: só o que não é óbvio lendo o código (armadilha, invariante, motivo de workaround) — curto, de preferência uma linha
  - se o porquê já está em `docs/`, aponta pra lá em vez de reexplicar
- Documentação em `docs/`:
  - doc = decisão tomada/estado desejado; distância pro código tem que ter item no todo (sem item: perguntar)
  - bug achado numa sessão só vira doc se o dono decidir
  - desenho ainda não implementado: detalhe completo (é a única fonte de verdade nesse momento)
  - depois de implementado: código vira fonte de verdade do *como*; a doc encolhe pro *porquê* (decisão de negócio/projeto, armadilhas encontradas)
  - cada fato mora num lugar só — duplicar entre código e doc(s) tende a ficar desatualizado
    - outros docs linkam pro dono do fato, não resumem
  - estado ("ainda não existe", "hoje é stub", "feito") só em `docs/todo.md` (detalhado) e no `README.md` (resumo)
    - `README.md`: por módulo, uma entrada por etapa do fluxo em que ele aparece (as caixas dos diagramas) — pra quem está chegando; sem detalhe de implementação
    - `docs/todo.md`: tarefas em nível de fluxo/feature — detalhe de implementação fica em `TODO` no código; item riscado não se repete no doc explicativo
    - docs de tópico descrevem desenho e porquê; pra saber o que falta, linkam o todo
    - "próximos passos" também vão pro todo, não pra dentro do doc de desenho
  - nada que o código responde sozinho: contagem de testes, tamanho de arquivo, listas que espelham um mapa do código
    - nome de classe/método/config: preferir link pro arquivo a repetir o nome solto
- Atualização de docs (fim de tarefa com código, ou decisão de desenho tomada em conversa) — sempre nesta ordem:
  1. reler: buscar tudo que o assunto toca em `docs/`, `README.md`, `CLAUDE.md` e comentários no código que citam docs
  2. propor o plano antes de editar: por arquivo, o que muda/sai/entra
     - separar decidido × em aberto: só vira decisão o que o dono confirmou explicitamente
     - em aberto vira item "decidir" no todo, nunca texto de desenho
  3. aplicar; o que falta fazer vai pro todo (marcar checkbox faz parte de terminar a tarefa)
  4. conferir de novo: docs entre si e contra o código (links, nomes, estado, afirmações que o diff tornou falsas)
- Escrita em geral (docs, TODOs, mensagens): bullets aninhados e frases curtas em vez de parágrafo denso

## Testes

Estratégia e regras obrigatórias em [docs/testing-strategy.md](docs/testing-strategy.md): teste de microsserviço usa sempre Testcontainers (`./mvnw test`); teste integrado entre serviços é `@Tag("integrado")` (`./mvnw package -DskipTests && ./mvnw test -Pintegrado`). Nenhum teste depende do compose de dev — só precisa do Docker no ar. Rodar módulo a módulo (`-pl`): máquina de 8 GB.

## Pendências

A mecânica de infraestrutura (filas, auth, config) está mais madura que a regra de negócio que deveria carregar. Em que pé está cada módulo: [README.md](README.md); lista detalhada: [docs/todo.md](docs/todo.md).

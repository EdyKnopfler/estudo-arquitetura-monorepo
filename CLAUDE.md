# Estudo Arquitetura — Agência de Viagens (Monorepo)

Monorepo de estudo simulando uma agência de viagens com transação distribuída entre passagens aéreas, hotel e pagamentos. O foco é praticar arquitetura (SAGA por coreografia, multi-módulo Maven, unidades de deploy separadas) — a regra de negócio real de cada domínio ainda está, em boa parte, por implementar. Documentação detalhada em [docs/](docs/README.md), indexada por tópico — carregue sob demanda.

## Foco (prioridade sobre o resto deste arquivo)

Não quero me limitar com excesso de regras: a doc serve pra eu saber o estado do projeto, o que já decidi, o que falta decidir. Posso mudar de ideia quantas vezes quiser, mas cada mudança deixa a doc coerente pra ser lida depois. Princípios gerais: não repetir informação; perguntar uma coisa de cada vez, sem bombardear; quando tiver que me contrariar ou questionar, comece resumindo e não assuma que algum lado está certo por padrão. Qualquer um deles cede a um motivo forte — é contexto e bom senso.

O controle é meu: eu decido escopo, regras e exceções. A doc é a regra geral; instrução no chat vale pro momento, a menos que eu peça pra atualizar a doc.

- Faça só o que foi pedido. Discutir não é executar: espere meu sinal, e ofereça ("posso fazer X").
- Ponto de conflito: pare e sinalize em uma linha, sem desenvolver — eu decido na hora:
  - "isso contraria a doc em X"
  - "a doc se contradiz em X" (pode ser exceção minha; só o que toca a tarefa)
  - "pedido aberto; leituras possíveis: …"
  - "impedimento: X"
  - "pendência fora do escopo: X" (no fim)
  - "preciso esclarecer N itens" (ver Ritmo da conversa)
- `docs/todo.md` e `docs/to-decide.md`: só leia quando eu mandar.
- Respostas curtas.

### Ritmo da conversa

Não despeje análise: eu penso um item por vez e dirijo o assunto.

- Várias coisas pra tratar: primeiro só a lista resumida, sem desenvolver.
- Depois, um item por mensagem: pergunte e espere. O item corrente só sai quando eu resolver.
- Desdobramento que surgir: uma linha, vai pro fim da fila — não discuta junto.
- Impedimento: sinalize; repriorizar é decisão minha.
- Fim de cada mensagem: até 2 linhas com os itens restantes.

## Stack

Java 25 (virtual threads), Spring Boot 4.0.1, Maven multi-módulo (8 módulos + parent POM), Postgres 18.1 (1 database por bounded context) + Flyway, RabbitMQ 4.2.2 (cliente Java cru), Docker Compose. Rodar local: `docker-compose up` (usa `.env` na raiz).

## Módulos

`clientes` · `sessaocompra` (roda por papel via profile `web`/`sagas`/`timeout`) · `reservas-{interno,externo}` (cada um roda 2x via profile `hotel`/`voo`; `reservas-interno` roda ainda 2x por papel via profile `web`/`sagas`) · `pagamento-{interno,externo}` (`pagamento-interno` roda 2x por papel via profile `web`/`sagas`, sem eixo de domínio) · `web-base` e `sagas-common` (bibliotecas transversais). Mapa completo, portas e diagrama de fluxo: [docs/architecture-overview.md](docs/architecture-overview.md).

## Decisões arquiteturais

- **`reservas-interno`, `pagamento-interno` e `sessaocompra` são cada um um artefato único**, com controller REST e listener de fila no mesmo processo — o papel ativo em cada instância é escolhido por profile Spring em runtime (`@Profile("web")`/`@Profile("sagas")`/`@Profile("timeout")`). Isso dá escala independente entre entrypoint REST e entrypoint de fila (N instâncias web, M instâncias fila) sem o custo de coordenar módulos Maven separados por domínio. `sessaocompra` tem ainda o papel `timeout` (job `@Scheduled`). Mecanismo em [docs/deploy-roles-by-profile.md](docs/deploy-roles-by-profile.md), organização interna da regra de negócio em [docs/module-boundaries.md](docs/module-boundaries.md).
- **SAGA por coreografia, não orquestração central.** Cada serviço só conhece a fila anterior/próxima; erro no handler dispara republish automático de compensação (`tipo=DESFACA`) retroativo na cadeia — mecânica em `sagas-common`. Cadeia `pagamento → hotel → voo`, com `sessaocompra` nas duas pontas (confirma a viagem no fim do sucesso, reverte a sessão no fim da compensação) — desenho em [docs/purchase-flow-design.md](docs/purchase-flow-design.md). Detalhe: [docs/saga-choreography.md](docs/saga-choreography.md).
- **Database por bounded context**, compartilhado pelas instâncias `web` e `sagas` do mesmo domínio (são a mesma unidade lógica de negócio, só split por entrypoint/escala).
- **Duas identidades de autenticação**: JWT para cliente final, client-id/secret por par de serviços internos. Detalhe e limitações conhecidas: [docs/security-and-auth.md](docs/security-and-auth.md).
- **Reconciliação a favor da venda.** O estado interno é presumido; a fonte da verdade são os `-externo`. Conflito de status se resolve consultando lá, e se lá o negócio fechou, seguimos daí em vez de cancelar. Simuladores ficam simples; resiliência é do nosso lado. Critério e exemplos: [docs/reconciliation.md](docs/reconciliation.md).
- **Endpoints orientados a estado.** Quem chama manda a intenção ("avançar", "voltar ao pedido"), quem recebe decide a ação pelo estado atual dos dois lados e responde a etapa. Implementa o caminho feliz; caso não tratado falha alto e entra depois, um por vez. Diretriz: [docs/state-driven-endpoints.md](docs/state-driven-endpoints.md).
- **Chaos engineering nos simuladores `-externo`**: falha e latência aleatórias propositais (`CHANCE_FALHA`), para exercitar os caminhos de compensação da SAGA.

## Escrita (código e docs)

Princípio: cada fato mora num lugar só, e quem é dono depende do tipo de fato. Os outros lugares linkam pro dono em vez de copiar — cópia desatualiza.

| Tipo de fato | Dono |
|---|---|
| *Como* funciona (já implementado) | o código |
| *Porquê* e o não óbvio: decisão, armadilha | doc de tópico em `docs/` |
| Desenho ainda não implementado | doc de tópico, em detalhe completo (única fonte até virar código) |
| O que falta, em nível de fluxo/feature | `docs/todo.md` |
| O que falta, em nível de implementação | `TODO` no código |
| Decisão em aberto | `docs/to-decide.md` |
| Em que pé está cada módulo, pra quem chega | `README.md` — uma entrada por etapa do fluxo, sem detalhe |

- Ao implementar, a doc encolhe pro porquê e pro não óbvio — pode resumir código quando isso explica algo.
- Estado e "próximos passos" só em `docs/todo.md`, `TODO` no código e `README.md`.
- Comentário no código só pro que a leitura não mostra, em uma linha; se o porquê está em `docs/`, aponte pra lá.
- Só vira decisão o que eu confirmei; o resto vai pro `to-decide.md`.

Atualizar docs (fim de tarefa com código, ou decisão tomada em conversa) — aqui ler `todo.md`/`to-decide.md` está autorizado:
1. Ache tudo que o assunto toca: `docs/`, `README.md`, `CLAUDE.md`, comentários que citam docs.
2. Proponha o plano por arquivo (muda/sai/entra), separando decidido × em aberto; espere meu sinal.
3. Aplique: marque no `todo.md` o que a tarefa concluiu, tire do `to-decide.md` o que foi decidido.
4. Confira só o que o diff tocou.

Forma: frases curtas e bullets aninhados em vez de parágrafo denso.

## Testes

Estratégia e regras obrigatórias em [docs/testing-strategy.md](docs/testing-strategy.md): teste de microsserviço usa sempre Testcontainers (`./mvnw test`); teste integrado entre serviços é `@Tag("integrado")` (`./mvnw package -DskipTests && ./mvnw test -Pintegrado`). Nenhum teste depende do compose de dev — só precisa do Docker no ar. Rodar módulo a módulo (`-pl`): máquina de 8 GB — suíte inteira: `./run-all-tests.sh`.

## Pendências

A mecânica de infraestrutura (filas, auth, config) está mais madura que a regra de negócio que deveria carregar. Em que pé está cada módulo: [README.md](README.md). Lista detalhada (só abra sob pedido): [docs/todo.md](docs/todo.md) (a implementar) e [docs/to-decide.md](docs/to-decide.md) (a decidir).

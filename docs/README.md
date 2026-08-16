# Índice de documentação

Detalhamento por tópico. O [CLAUDE.md](../CLAUDE.md) na raiz traz o resumo executivo com links para cá — carregue cada arquivo abaixo sob demanda, conforme o que a tarefa exigir, não é necessário ler tudo de uma vez.

- [architecture-overview.md](architecture-overview.md) — mapa de componentes, portas, bancos de dados e fluxo de uma compra ponta a ponta
- [saga-choreography.md](saga-choreography.md) — mecânica da coreografia SAGA (filas, mensagem, compensação) e o que está ligado vs. stub
- [purchase-flow-design.md](purchase-flow-design.md) — desenho (não implementado) de como `sessaocompra` amarra pré-reservas, pagamento e a SAGA estendida
- [module-boundaries.md](module-boundaries.md) — organização interna da regra de negócio (`reservas-interno`/`pagamento-interno`) e alternativas avaliadas (hexagonal, contrato tipado da SAGA)
- [deploy-roles-by-profile.md](deploy-roles-by-profile.md) — como o papel (web vs. sagas) de cada módulo é escolhido por profile Spring, mecanismo e pegadinhas
- [security-and-auth.md](security-and-auth.md) — JWT, client-id/secret entre serviços, limitações conhecidas
- [web-base-hardening.md](web-base-hardening.md) — decisões de segurança do `web-base`: wiring da `SecurityFilterChain`, validação de JWT (`iss`/`kid`), client-secret, tratamento de erro — o que existe e por quê
- [testing-strategy.md](testing-strategy.md) — Testcontainers vs. serviços já no ar (`docker-compose up`), quando usar cada um
- [todo.md](todo.md) — lacunas técnicas encontradas em revisão de arquitetura (complementa o checklist de features do [README.md](../README.md))

Conforme o conteúdo de um tópico crescer, prefira quebrar em subarquivos (ex.: `saga-choreography/compensacao.md`) e indexar aqui, em vez de inflar um único arquivo.

# A decidir

Decisões em aberto. O que já está decidido e falta implementar fica em [todo.md](todo.md).

## Features por domínio

### Sessão de compra — ponta da SAGA

* [ ] modelagem de status da reversão (`DESFACA` em `sessaocompra`):
  * diferenciar falha de negócio × erro de execução (inclui serviço externo) — status próprio (`REVERTENDO`?) ou informação no payload
  * terminar em `ERRO` depende dessa distinção (erro de execução, não falha de negócio)
  * diagramas do README só depois de fechar essa modelagem
* [ ] a considerar na reversão: remover as reservas que falharam e notificar o usuário, que decide se volta pra tentar outras opções
* [ ] fim da SAGA com a sessão fora de `PAGAMENTO_EFETUADO` (ex.: o `PUT` da sessão ainda com o job de pendências) — provisório: fila de erros, sem encaminhar
  * inclui o `DESFACA` de volta quando `sessaocompra` falha ao confirmar — hoje a fila `sessaocompra` não tem anterior (exceção no `DESFACA` faria loop)

### Pagamentos

* [ ] se consultar e cancelar do externo têm falha proposital (chaos)
* [ ] webhook:
  * [ ] como correlacionar webhook de tentativa antiga — a linha só guarda o `id_externo` vigente
  * [ ] duas tentativas pagas na mesma sessão
* [ ] falha no estorno (cliente cobrado sem viagem) — erro catastrófico, a modelar; provisório: fila de erros, sem reverter a sessão

## Lacunas de arquitetura

### Contrato da mensagem da SAGA

- [ ] contrato da mensagem ([module-boundaries.md](module-boundaries.md#contrato-tipado-para-a-mensagem-da-saga)): `Map<String, Object>` genérico (`Messaging`) ou DTO próprio da fila (ids de correlação da sessão + ids de reserva/pagamento)
  - a favor do DTO: não reaproveitar entidade JPA nem DTO de REST — contratos com motivos de mudança diferentes
  - ganha peso com os ids de negócio na mensagem ([payload](purchase-flow-design.md#payload-da-mensagem-da-saga))

### Erros catastróficos

- [ ] tratamento de erros catastróficos usando a DLQ: hoje os provisórios (falha no estorno; fim da SAGA com a sessão fora do estado) só caem na fila `errors`, sem ninguém consumir

### Estados intermediários

- [ ] estado intermediário que guarda chamada externa (ex.: status de intenção em `Reserva`): como não deixá-lo preso
  - timeout próprio pra cada um, ou limpar a partir do timeout da sessão

### Testes

- [ ] RabbitMQ de teste é um container só (reuse) pra `reservas-interno` e `pagamento-interno` — com consumidor real, filas de contextos diferentes podem se misturar; avaliar isolar (vhost por módulo)

### Pontos a revisitar (não são bugs)

- webhook publicar direto em `hotel`, com `pagamento` só como fila anterior: no sucesso o pagamento só repassa (a cobrança já aconteceu) — por ora o repasse fica (sem código, aparece no rastreio)
- Prioridade bem baixa: relógio da sessão começa na criação, não na primeira pré-reserva — conservador, os prazos dados se supõem suficientes. Começar na primeira pré-reserva ganharia o tempo até a primeira escolha, mas sessão sem escolha ficaria sem relógio pro timeout de `INICIADA`.
- `basicQos(1)` limita cada instância de SAGA a processar uma mensagem por vez — teto de throughput conhecido, revisitar se/quando houver medição de carga real.
- `JWT_PUBLIC_KEY`/`JWT_PRIVATE_KEY` no `.env`/`.env.clientes` não seguem o padrão `<SERVIÇO>_ID`/`_SECRET` do resto do arquivo (são infra compartilhada tipo `DB_HOST`, não credencial de um par específico) — considerar renomear pra algo tipo `CLIENTE_JWT_PUBLIC_KEY` se ficar confuso.
- Sem captura/agregação centralizada de log — cada instância só loga local (SLF4J). Fora de escopo por agora; talvez um dia uma infra pra isso (sidecar → Elasticsearch, CloudWatch ou o que estiver à mão).
- **Segurança que hoje assume "só roda em localhost"** — sem desenho de deploy de produção ainda, cada item abaixo fica pendente de revisão isolada quando esse desenho começar:
  - JDWP (`JAVA_TOOL_OPTIONS=-agentlib:jdwp=...,address=*:PORT`) exposto e publicado no host em todo serviço, pra attach de debugger — JDWP não tem autenticação (debugger anexado = RCE). Aceitável hoje (dev local); não pode existir fora de `localhost`.
  - Secrets vivem em `.env`/`.env.<serviço>` sem gestão real (vault, secrets manager de nuvem) — aceitável hoje porque são credenciais de dev descartáveis (só `.env*.example` é versionado).
  - Nenhuma comunicação usa TLS (front↔`clientes`/`sessaocompra`, serviço↔serviço, app↔Postgres/RabbitMQ) — tudo HTTP/AMQP puro na rede Docker local.
  - Sem rate limit/lockout em `/login` (`clientes`) — decisão consciente de deixar como responsabilidade de borda (gateway/WAF), não da aplicação: throttling por IP não precisa entender o payload, é mais barato bloqueado antes da app, e ajusta sem redeploy. Reavaliar se algum dia precisar de lockout por `email` (semântica de negócio que a borda não enxerga sozinha).

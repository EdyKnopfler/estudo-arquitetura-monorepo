# Fluxo de compra — desenho (não implementado)

Resultado de uma sessão de arquitetura (2026-08-02) sobre como `sessaocompra` amarra pré-reservas, pagamento e a SAGA. **A seção "Interação do usuário" abaixo (até o disparo do pagamento) está implementada** (sessão de 2026-08-09) — falta só a "SAGA estendida" mais abaixo, que continua desenho. Complementa [saga-choreography.md](saga-choreography.md) (mecânica já implementada) e [todo.md](todo.md) (lacunas atuais).

## Interação do usuário

Implementado em `SessaoCompraController`/`SessaoCompraService`/`SessaoCompraRepository` (código é a fonte de verdade pro mecanismo). Decisões de negócio por trás:

1. `idCliente` vem do JWT só na criação da sessão, nunca de update posterior. Cliente pode ter **múltiplas sessões simultâneas** (decisão deliberada, não uma-por-cliente); ownership por sessão via `@PreAuthorize` (ver [security-and-auth.md](security-and-auth.md)).
2. Re-seleção de item já escolhido faz troca de verdade (não cria e ignora a antiga): adquire a nova antes de liberar a antiga, nunca ao contrário — evita deixar o cliente sem nada se a nova falhar. Liberação da antiga é melhor esforço; o timeout do próprio `reservas-externo` é a rede de segurança.
3. `sessaocompra` é o único ponto de contato do front ("porteiro") — front nunca fala direto com `reservas-interno`/`pagamento-interno`, nem sabe os ids de reserva.

## Dois timeouts

- **`TimeoutTask` (existente)**: sessões em `INICIADA` que passam de `TEMPO_MAXIMO` sem completar as reservas e iniciar pagamento → cancela.
- **novo, planejado (`TimeoutPagamentoTask`)**: sessões em `EFETUANDO_PAGAMENTO` que passam de uma janela própria (mais longa, alinhada à validade do meio de pagamento — PIX/redirect de gateway) sem confirmação → expira. Precisa de uma coluna de timestamp própria pro início do pagamento (`start_time` hoje só marca o início da sessão inteira).

Os dois competem com a mudança de estado feita pelo mesmo tipo de update condicional guardado por `status` (`WHERE status = '...'`) já usado em `iniciarPagamento`/`marcarLoteComoCancelando` — quem mudar o status primeiro no banco "vence"; o outro não encontra mais linha pra afetar.

## SAGA estendida — sessaocompra como bookend do anel

Cadeia de negócio hoje é `pagamento → hotel → voo` (mecânica em [saga-choreography.md](saga-choreography.md)). O desenho estende o anel com dois nós novos que fazem update local em `SessaoCompra`, reaproveitando o mesmo mecanismo de "handler lança exceção → compensação automática pra trás" do `sagas-common` — sem framework novo.

```mermaid
flowchart LR
  W[webhook pagamento] -->|sucesso| PAG
  W -->|falha| REV[sessaocompra: reverte]

  PAG[pagamento: confirma] -->|EXECUTE| HOT[hotel: confirma]
  HOT -->|EXECUTE| VOO[voo: confirma]
  VOO -->|EXECUTE| CONF[sessaocompra: confirma → VIAGEM_RESERVADA]

  CONF -.DESFACA, se falhar.-> VOO
  VOO -.DESFACA.-> HOT
  HOT -.DESFACA.-> PAG
  PAG -.DESFACA, estorno.-> REV
```

- **Sucesso**: webhook de pagamento → confirma em `pagamento` → `hotel` → `voo` → `sessaocompra` marca `VIAGEM_RESERVADA`. Fim de cadeia.
- **Falha em qualquer etapa (inclusive em `sessaocompra: confirma`)**: propaga DESFACA pra trás até `sessaocompra: reverte`, que volta a sessão pro estado anterior e reseta o timer de expiração (dá mais tempo pro usuário escolher outra opção de voo/hotel/pagamento).
- **Falha direto no webhook** (pagamento recusado, nada chegou a ser confirmado): pula o anel inteiro, vai direto pra `sessaocompra: reverte` — não há nada em `pagamento`/`hotel`/`voo` pra desfazer.
- **Discard vs. reverter**: quem detecta a falha de confirmação (ex. `voo`, item não disponível mais no fornecedor) trata isso como erro local *antes* de publicar DESFACA — zera a própria pré-reserva. Quem só recebe DESFACA nunca é quem falhou (por construção da coreografia), então sempre faz a mesma ação: reverter pra pré-. Não precisa de flag na mensagem pra essa distinção — mas a mensagem ainda precisa de um id de correlação (sessão de compra) pra cada handler saber qual linha local afetar (item já pendente, ver [todo.md](todo.md)).
- Consequência: `sessaocompra` passa a ter um consumidor de fila mínimo pros dois nós novos. Hoje ela não depende de `sagas-common` nem participa da coreografia — isso muda com esse desenho (ver nota em [CLAUDE.md](../CLAUDE.md) e [deploy-roles-by-profile.md](deploy-roles-by-profile.md), que hoje afirmam o contrário como decisão tomada).
- **Mecanismo de fiação — reaproveita `proximafila`/`filaanterior`, não precisa de "dois nós" de verdade.** Os dois pontos de contato do diagrama colapsam numa única fila nova (`sessaocompra`), porque o protocolo já resolve isso: configurar `proximafila: sessaocompra` em `voo` (hoje sem `proximafila`, fim da cadeia) e `filaanterior: sessaocompra` em `pagamento` (hoje sem `filaanterior`, início da cadeia) é suficiente — `SagasMessaging.iniciarConsumo` já publica em `filaProximoServico` no caminho `EXECUTE` e em `filaServicoAnterior` no caminho `DESFACA` (ver [saga-choreography.md](saga-choreography.md)). Um único handler em `sessaocompra`, igual aos outros, recebe as duas direções na mesma fila e decide o que fazer olhando o campo `tipo` da mensagem (`EXECUTE` → confirma; `DESFACA` → reverte) — mesmo padrão que `ReservasSagas`/`PagamentoSagas` já vão seguir.
- **Caso "falha direto no webhook" precisa de publish fora do fluxo normal.** Publicar `DESFACA` direto em `sessaocompra` sem passar pelo anel exige que o profile `web` de `pagamento-interno` também consiga publicar mensagem (hoje só o profile `sagas`, via `SagasWiring`, tem essa capacidade) — é a mesma lacuna já registrada em [todo.md](todo.md) pro caso de sucesso (webhook precisa publicar `EXECUTE` em `pagamento`); os dois casos (sucesso e falha do webhook) resolvem juntos quando essa capacidade de publish existir no profile `web`.

## Status de `Reserva` e não-idempotência de `reservas-externo`

Dois axiomas assumidos pra esse simulador (decisão de design deliberada — não é ponto a reavaliar):

1. **Cancelamento/expiração automática do lado externo é confiável.** TTL de 15min em `criar`/`confirmar` (`ReservasService`/`ReservasRepository`, `reservas-externo`); os dois são `@Transactional`, então falha explícita = zero efeito colateral. Consequência: o caminho de compensação (cancelar/liberar) não precisa de entrega garantida — melhor esforço basta, mesmo padrão que `ReservasService.liberarMelhorEsforco` (`reservas-interno`) já usa.
2. **`confirmar`/`remover` são e continuam não-idempotentes.** Guarda estrita `WHERE confirmado = false` em `ReservasRepository` (`reservas-externo`) — uma segunda chamada depois de sucesso real dá o mesmo erro (`EntityNotFoundException`) de uma falha real, sem key nem endpoint de consulta pra desambiguar. Toda a responsabilidade de nunca chamar `confirmar` duas vezes cai em `reservas-interno` — sem ajuda do lado de lá.
3. **`reservas-externo` precisa de um endpoint de consulta (`consultar`).** Sem ele, um timeout em `confirmar` é ambiguidade irredutível — dado o axioma 2, sucesso e falha real são indistinguíveis sem perguntar de volta à fonte de verdade. É o que torna o resto deste desenho possível; ainda não implementado (ver [todo.md](todo.md)).

### Arquitetura: caminho feliz síncrono, caminho lento em background

- **caminho feliz** (chamada externa dá resposta definitiva — sucesso ou falha de negócio): continua síncrono, `sagas-common` como está hoje ou com extensão leve — sem delay, sem task.
- melhora no framework SAGAS (`SagasMessaging`) — estende, não troca:
  - suportar erros "tratados" (de negócio) com ack + publica para trás — pula a DLQ, que fica só pra falha sistêmica/inesperada
  - suportar "ack" puro para retentativa em caso de falha em *obter resposta* do serviço externo — obrigatório: `basicQos(1)` faz segurar sem ack travar a instância consumidora inteira
- status de intenção + task de retentativa (não redelivery do RabbitMQ)
  - novo status de intenção + contador de tentativas em `Reserva` (nomes ainda em aberto; hoje só `id`+`idExterno`, migration já tem `-- TODO falta status`), transição idempotente (`WHERE` aceita estado anterior ou já-no-alvo)
  - idempotente
  - limite de vezes por reserva — sobre tentativas de *obter resposta* do `consultar` (axioma 3 acima), não de `confirmar`
  - também realiza tarefas de fila
    - chama `consultar` pra saber o estado real (seguro de repetir — leitura pura)
    - sequência SAGAS para frente (sucesso) ou para trás (falha de negócio), via `SagasMessaging.publicar()` — só o primitivo de publish, o loop de ack/nack de `iniciarConsumo` não serve aqui
    - publica *antes* de marcar status terminal — se a escrita cair no meio, o próximo tick refaz com segurança; pior caso é mensagem duplicada, downstream já tolera at-least-once (ordem inversa exigiria um segundo scan estilo outbox)
    - quando estoura o limite de tentativas de *obter resposta*: publica mensagem corrente na DLQ manualmente (`basicPublish` direto, sem delivery tag pra `nack`) + publica para trás
      - *esse* é o único momento em que faz sentido para a gente jogar o cara para a DLQ

**Gap concreto, independente dos axiomas acima:** `remover` também exige `WHERE confirmado = false` — hoje não existe operação em `reservas-externo` pra desfazer uma reserva já confirmada. Isso bloqueia de verdade o caso "DESFACA chega numa `Reserva` já `RESERVADA`, reverte pra pré-" descrito em "Discard vs. reverter" acima. Precisa de um endpoint tipo `desconfirmar`/estorno em `reservas-externo` antes desse caminho de compensação funcionar ponta a ponta.

**Nota relacionada (achado separado, mesmo handler):** `SagasMessaging.iniciarConsumo` hoje dá `basicAck` da mensagem recebida **antes** de publicar a próxima na cadeia (`SagasMessaging.java`) — uma queda nesse intervalo perde a publicação sem redelivery (mensagem já foi consumida). Não afeta o status de intenção acima (esse depende do ack da mensagem *de entrada*, que só acontece depois do handler terminar), mas é outro furo de mesma natureza a corrigir na mesma área.

## O que isso desbloqueia / próximos passos

- ~~Endpoints incrementais por tipo de reserva~~ — feito.
- ~~Gate de completude em `iniciarPagamento`~~ — feito.
- ~~Client-id/secret `sessaocompra` → `reservas-interno`~~ — feito, reaproveitando credenciais existentes (ver [security-and-auth.md](security-and-auth.md)).
- ~~`SecurityConfiguration`/`@PreAuthorize` em `sessaocompra`~~ — feito.
- ~~Endpoint de troca em `reservas-interno`~~ — feito, ver item 2 acima.
- `TimeoutPagamentoTask` + coluna de timestamp do início do pagamento.
- Payload da mensagem SAGA com id de correlação.
- Fila nova `sessaocompra` + handler único (branch por `tipo`), `voo.proximafila`/`pagamento.filaanterior` apontando pra ela.
- Capacidade de publish no profile `web` de `pagamento-interno` (hoje só `sagas` publica) — necessária pro webhook de sucesso e de falha.
- Implementação real dos handlers de negócio em `ReservasSagas`/`PagamentoSagas` (ver [todo.md](todo.md)).
- Ordem de start-up `sessaocompra-web` × `reservas-interno-*-web` no `docker-compose.yml` (checar ciclo em `depends_on`).
- Robustez do fluxo confirmar/reverter `Reserva` (endpoint `consultar`, endpoint `desconfirmar`/estorno, dois desfechos novos em `SagasMessaging`, status de intenção + task de retentativa, handler de `ReservasSagas`) — quebra em tarefas em [todo.md](todo.md), seção "Dual-write pagamento/reservas".
- Reordenar `ack`/publish em `SagasMessaging` (ver nota acima).

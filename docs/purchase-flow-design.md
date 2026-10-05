# Fluxo de compra — desenho

Resultado de uma sessão de arquitetura (2026-08-02) sobre como `sessaocompra` amarra pré-reservas, pagamento e a SAGA. Complementa [saga-choreography.md](saga-choreography.md) (mecânica de fila). O que falta implementar: [todo.md](todo.md). Diagramas (estados da sessão e SAGA): [README](../README.md#como-funciona).

## Interação do usuário

Mecanismo em [SessaoCompraController](../sessaocompra/src/main/java/com/derso/arquitetura/sessaocompra/app/SessaoCompraController.java)/[SessaoCompraService](../sessaocompra/src/main/java/com/derso/arquitetura/sessaocompra/SessaoCompraService.java). Decisões de negócio por trás:

1. `idCliente` vem do JWT só na criação da sessão, nunca de update posterior. Cliente pode ter **múltiplas sessões simultâneas** (decisão deliberada, não uma-por-cliente); ownership por sessão via `@PreAuthorize` (ver [security-and-auth.md](security-and-auth.md)).
2. Re-seleção de item já escolhido faz troca de verdade (não cria e ignora a antiga): adquire a nova antes de liberar a antiga, nunca ao contrário — evita deixar o cliente sem nada se a nova falhar. Liberação da antiga é melhor esforço; o timeout do próprio `reservas-externo` é a rede de segurança.
3. `sessaocompra` é o único ponto de contato do front com o nosso backend ("porteiro") — front nunca fala direto com `reservas-interno`/`pagamento-interno`, nem sabe os ids de reserva.
   - fora do backend, o front só acessa a URL de pagamento do gateway (ver [Criação do pagamento](#criação-do-pagamento))

## Dois timeouts

- **timeout da sessão** (`TimeoutTask`): só pega sessão em `INICIADA` (pendente de criar pagamento) → cancela e **desfaz as pré-reservas efetuadas**.
  - serviços dão erro e nem o cancelamento consegue seguir → `FALHA_CANCELAMENTO`, terminal — registra o caso em vez de perdê-lo
- **timeout do pagamento** (`TimeoutPagamentoTask`): tempo pro cliente pagar, sessão em `EFETUANDO_PAGAMENTO` — janela própria, alinhada à validade do meio de pagamento (PIX/redirect de gateway)
  - estourou → mesma operação do [cancelamento](#cancelamento-e-prazo--gateway-como-juiz): pede cancelamento ao gateway; já pago segue o fluxo, senão volta a sessão pra `INICIADA`, pro cliente iniciar outro pagamento
- relógio: enquanto corre o timeout do pagamento, o tempo da sessão não conta

Os dois competem com a mudança de estado pelo mesmo tipo de update condicional guardado por `status` (`WHERE status = '...'`) de `iniciarPagamento`/`marcarLoteComoCancelando` — quem mudar o status primeiro no banco "vence"; o outro não encontra mais linha pra afetar.

## Criação do pagamento

Desenho de 2026-09-27. O que falta decidir e implementar: [todo.md](todo.md#features-por-domínio).

### Cadeia idempotente

front → `sessaocompra` (`iniciando-pagamento`) → `pagamento-interno` (`PUT /pagamentos/{idSessao}`) → gateway (criar)

- em cada salto, retentar = "consultar ou gerar": se a tentativa anterior gerou algo, a retentativa devolve isso; senão, gera
- nenhum salto reverte o próprio estado ao falhar — quem chamou só retenta
- nenhum salto retenta sozinho (nem na requisição, nem por job): quem dispara retentativa é o front
  - gerar outra transação quando o gateway devolve uma expirada não é retentativa, é reconciliação ([abaixo](#pagamento-da-sessão-tentativa-trocável))
  - sessão volta pra `INICIADA` só por timeout do pagamento ou cancelamento explícito ([abaixo](#cancelamento-e-prazo--gateway-como-juiz)) — inclusive o pedido pela [compensação da SAGA](#saga-estendida--sessaocompra-como-bookend-do-anel)
- `pagamento-interno` é o guardião da URL do nosso lado: `sessaocompra` não guarda, repete o `PUT` a cada `iniciando-pagamento`
  - vale com a sessão em `CRIANDO_PAGAMENTO` ou já em `EFETUANDO_PAGAMENTO` (o front pode ter perdido a resposta)
- URL obtida só é substituída depois de cancelada ou vencida no gateway

### Premissas do gateway simulado (`pagamento-externo`)

Imita gateways reais no que importa pro backend — o front é abstraído, não há tela. Simples e ruim de propósito: resiliência e reconciliação ficam do nosso lado ([reconciliation.md](reconciliation.md)).

- criar e pagar são endpoints separados
  - criar: chamado por `pagamento-interno` (client-id/secret), devolve `idTransacao` + URL de pagamento fictícia
    - só cria se o par (chave de idempotência, cliente) não existe; se existe, devolve o estado atual da transação
    - falha de propósito (chaos): falhou em criar — não existe "recusa" na criação
      - antes ou depois de gravar; depois é a falha ambígua (transação criada, resposta perdida), que o chamador resolve repetindo a chave
  - pagar: simula o usuário na tela do gateway — chamado por quem tem a URL, sorteia o desfecho (aceito ou recusado) e chama o webhook de forma síncrona
    - síncrono: a simulação mais simples, por enquanto
    - marca pago e avisa sem outbox (um gateway real precisaria): webhook que falha só vai pro log, a transação continua paga e quem pagou recebe sucesso — o nosso lado descobre pelo consultar; o dual-write bem tratado é o da confirmação no interno
    - pagar de novo uma transação já paga responde pago, sem reenviar o webhook
    - falha de propósito (chaos) em dois momentos:
      - recusa: nada gravado, webhook de recusa
      - pago sem aviso: marca pago e cai antes do webhook — falha técnica não é reportada (limitação do simulador); o nosso lado só descobre pelo consultar
    - só sucesso responde 2xx; recusa e expiração, 4xx; falha simulada, 5xx
    - recusa é entre o usuário e o gateway: a transação continua pendente e a URL segue valendo pra tentar de outra forma
  - pagar não autentica: a URL é a credencial — valor e destino já foram fixados na criação
- prazo fixo desde a criação ([Pagamento](../pagamento-externo/src/main/java/com/derso/arquitetura/pagamentoexterno/entity/Pagamento.java)), por relógio (sem job); depois dele, pagar responde expirado
  - ninguém é avisado da expiração — gateways reais costumam notificar; aqui o nosso lado descobre pelo próprio timeout + consulta
  - transação cuja URL nunca foi entregue nunca é paga — só expira
- consultar: devolve a situação atual da transação — base da reconciliação
- cancelar: encerra a transação pendente antes do prazo (cancelada ≠ expirada: expirada é a pendente fora do prazo, sem pedido de ninguém)
  - já paga: responde 4xx e continua paga — quem pediu reconcilia a partir disso
  - já expirada: responde expirada
- consultar e cancelar só enxergam transações do cliente que as criou

### Dual-write na criação

Criar envolve o gateway e o nosso banco, sem transação comum. Chamar o gateway antes de gravar deixa, num timeout ou numa queda antes do save, uma transação lá sem registro aqui.

- a linha em `pagamentos` é gravada antes da chamada, com status de intenção (`CRIANDO`)
- a resposta do gateway completa a linha (`id_externo`, URL, `AGUARDANDO_PAGAMENTO`); sem URL, a linha continua em `CRIANDO`
- pior caso vira "registro de algo que talvez não exista lá" (recuperável), em vez de "algo lá sem registro aqui"
- risco baixo aqui: transação perdida só custa criar outra — a antiga expira no gateway (premissas acima). Bem menos crítico que o dual-write do webhook ([todo.md](todo.md#dual-write-pagamentoreservas-outbox-das-reservas-descartado--ver-desenho))

### Pagamento da sessão, tentativa trocável

- uma linha em `pagamentos` por sessão de compra: é o controle do pagamento da sessão, não uma tentativa
  - `PUT /pagamentos/{idSessao}`: repetir leva ao mesmo estado (mesmo pagamento, mesma URL)
- a tentativa é a chave de idempotência da linha (e o `id_externo` que ela rende), trocada no lugar:
  - cada tentativa nasce com uma chave nova, gravada antes da chamada
  - falha ambígua (timeout, 5xx): próxima chamada repete com a mesma chave — se a transação foi criada, o gateway devolve a mesma, sem criar outra
  - gateway devolve transação expirada (chave repetida tarde, depois de uma falha ambígua): troca a chave e cria outra na mesma chamada — o `PUT` só chega com a sessão viva, então o prazo do gateway não importa ao interno
  - troca de chave é update condicional (`WHERE chave = :antiga`), contra chamadas concorrentes
  - obtida a URL: toda chamada seguinte devolve a mesma, sem chamar o gateway
- a regra é "já obtivemos URL", não "já entregamos": se ela chegou ao usuário não sabemos, e não importa — devolvemos a mesma
- o webhook correlaciona por `id_externo` (passo 5 da cadeia abaixo)

### Cancelamento e prazo — gateway como juiz

Cancelar × pagar e prazo × pagar são corridas; quem decide é o gateway, fonte da verdade de "foi pago" — critério em [reconciliation.md](reconciliation.md).

- o front pode cancelar o pagamento a qualquer momento antes de pago, com ou sem URL — sessão volta pra `INICIADA`
  - sem URL: nada a consultar
  - com URL: pede cancelamento ao gateway — já pago faz o pagamento vencer e seguir o fluxo
  - cancelado, o próximo `PUT` abre tentativa nova — chave nova, ids de reserva atuais
- timeout do pagamento é a mesma operação, com outro gatilho; nosso prazo é maior que o do gateway — quando ele dispara, a URL já venceu e a consulta é definitiva
- pagamento aceito que chega pelo webhook é reconciliado a favor da venda, mesmo fora da tentativa vigente

## SAGA estendida — sessaocompra como bookend do anel

A cadeia base é `pagamento → hotel → voo` (mecânica em [saga-choreography.md](saga-choreography.md)). O desenho estende o anel com dois nós que fazem update local em `SessaoCompra`, reaproveitando o mesmo mecanismo de compensação pra trás do `sagas-common` — sem framework novo.

Diagrama: [README](../README.md#saga-disparada-a-partir-do-webhook-de-pagamento).

- **Sucesso**: webhook de pagamento marca a sessão como `PAGAMENTO_EFETUADO` e dispara o anel → confirma em `pagamento` → `hotel` → `voo` → `sessaocompra` marca `VIAGEM_RESERVADA`. Fim de cadeia.
- **Falha em qualquer etapa (inclusive em `sessaocompra: confirma`)**: propaga DESFACA pra trás até `sessaocompra: reverte` — cancelamento explícito do que já foi fechado na sessão: volta pra `INICIADA` e reseta o timer de expiração (dá mais tempo pro usuário escolher outra opção de voo/hotel/pagamento).
  - modelagem de status da reversão (falha de negócio × erro de execução, quando terminar em `ERRO`): em aberto — ver [todo.md](todo.md#features-por-domínio)
- **Discard vs. reverter**: quem detecta a falha de confirmação (ex. `voo`, item não disponível mais no fornecedor) trata isso como erro local *antes* de publicar DESFACA — zera a própria pré-reserva. Quem só recebe DESFACA nunca é quem falhou (por construção da coreografia), então sempre faz a mesma ação: reverter pra pré-. Não precisa de flag na mensagem pra essa distinção — mas precisa saber qual linha local afetar (ver "Payload da mensagem da SAGA" abaixo).
- Consequência: `sessaocompra` vira a ponta final da reversão da SAGA (e do sucesso) — ganha um papel `sagas` mínimo pros dois nós e passa a depender de `sagas-common`.
- **Mecanismo de fiação — reaproveita `proximafila`/`filaanterior`, não precisa de "dois nós" de verdade.** Os dois pontos de contato de `sessaocompra` no anel colapsam numa única fila (`sessaocompra`): `proximafila: sessaocompra` em `voo` e `filaanterior: sessaocompra` em `pagamento` bastam — `Messaging.iniciarConsumo` publica na próxima fila no encaminhamento pra frente e na anterior no encaminhamento pra trás (ver [saga-choreography.md](saga-choreography.md)). Um único handler em `sessaocompra`, igual aos outros, recebe as duas direções na mesma fila e decide pelo campo `tipo` (`EXECUTE` → confirma; `DESFACA` → reverte) — mesmo padrão de `ReservasSagas`/`PagamentoSagas`.

## Payload da mensagem da SAGA

Cada handler precisa agir num recurso específico (a reserva, o pagamento, a sessão) — só `tipo` + `rastreio` não dizem qual. Decisão: em vez de um id de correlação genérico + busca em cada elo, a mensagem carrega os ids internos que cada handler precisa, desde a primeira publicação no webhook.

**Só ids internos viajam na mensagem — nunca `idExterno`** (todos populados uma vez, na origem):

- `idSessaoCompra` — nós bookend de `sessaocompra` (`confirma`/`reverte`, ver "SAGA estendida" acima).
- `idPagamento` — id interno (PK) da linha em `pagamentos` (`pagamento-interno`), usado por `PagamentoSagas`.
- `idReservaHotel` — id interno (PK) da linha em `reservas`, usado por `ReservasSagas` (profile `hotel`).
- `idReservaVooIda` / `idReservaVooVolta` — mesmo papel, pro profile `voo`. **Nota:** a cadeia tem um único nó `voo`, mas a sessão de compra tem duas reservas de voo — o handler de `voo` age nas duas a partir da mesma mensagem; forma exata (chamadas sequenciais? o que acontece se uma falhar e a outra não?) em aberto, item no [todo.md](todo.md).

**Por que não `idExterno` também:** cada instância `sagas` (`ReservasSagas`/`PagamentoSagas`) roda no mesmo processo/banco que o papel `web` do mesmo domínio — não é um serviço separado, é só outro profile do mesmo artefato (ver [deploy-roles-by-profile.md](deploy-roles-by-profile.md)). Achar `idExterno` a partir do `idReserva`/`idPagamento` é um `findById` pela PK, local, indexado — não é o tipo de busca que a mensagem "rica" tenta evitar. O que se evita são duas coisas bem diferentes:
- `idExterno` cruzar a fronteira de `reservas-interno`/`pagamento-interno` sem necessidade — não é assunto de `sessaocompra`, do webhook, nem da mensagem da SAGA; é detalhe interno de como cada domínio fala com seu provedor externo. `reservas-interno` e `pagamento-interno` devolvem só o `id` interno.
- O webhook sair chamando os outros serviços via HTTP só pra montar a mensagem — isso sim seria excesso de chamada de rede pra buscar algo que cada serviço já tem local, no próprio banco.

**Cadeia de propagação:**

1. `sessaocompra` guarda `idReservaHotel`/`idReservaVooIda`/`idReservaVooVolta`.
2. `sessaocompra` → `pagamento-interno` (`PUT /pagamentos/{idSessao}`) passa `idSessaoCompra` + os 3 `idReserva*` numa tacada só.
3. `pagamento-interno` grava a linha da sessão em `pagamentos` (PK própria, `idSessaoCompra`, os 3 `idReserva*`) antes de chamar o gateway, e completa com `id_externo = idTransacao` na resposta — ver [Criação do pagamento](#criação-do-pagamento).
4. O callback do `pagamento-externo` (webhook) carrega `idTransacao` + `status` — sem `idTransacao` não dá pra correlacionar a resposta com a linha certa.
5. O webhook em `pagamento-interno` busca `pagamentos WHERE id_externo = idTransacao` (id de correlação natural desse par requisição/resposta) e monta a mensagem da SAGA com `idPagamento` — **a PK da linha (`pagamentos.id`), não `idTransacao`** — mesma distinção de `idReserva`/`idExterno` em reservas: `idTransacao` só serve pra achar a linha aqui, nunca viaja na mensagem. Publica.
6. `ReservasSagas`/`PagamentoSagas`: cada um lê da mensagem só o id que lhe interessa e faz `findById` local pra pegar `idExterno` (e o resto que precisar) do próprio banco.

Ou seja: nenhum serviço recebe uma "mensagem web" só pra ir buscar algo no banco e devolver — cada chamada de rede nessa cadeia já carrega dado de negócio que precisava viajar de qualquer forma (pedir/confirmar pagamento).

## Status de `Reserva` e não-idempotência de `reservas-externo`

Dois axiomas assumidos pra esse simulador (decisão de design deliberada — não é ponto a reavaliar):

1. **Cancelamento/expiração automática do lado externo é confiável.** TTL de 15min em `criar`/`confirmar` (`ReservasService`/`ReservasRepository`, `reservas-externo`); os dois são `@Transactional`, então falha explícita = zero efeito colateral. Consequência: o caminho de compensação (cancelar/liberar) não precisa de entrega garantida — melhor esforço basta, mesmo padrão de `ReservasService.liberarMelhorEsforco` (`reservas-interno`).
2. **`confirmar`/`remover` são e continuam não-idempotentes.** Guarda estrita `WHERE confirmado = false` em `ReservasRepository` (`reservas-externo`) — uma segunda chamada depois de sucesso real dá o mesmo erro (`EntityNotFoundException`) de uma falha real, sem key nem endpoint de consulta pra desambiguar. Toda a responsabilidade de nunca chamar `confirmar` duas vezes cai em `reservas-interno` — sem ajuda do lado de lá.
3. **`reservas-externo` precisa de um endpoint de consulta (`consultar`).** Sem ele, um timeout em `confirmar` é ambiguidade irredutível — dado o axioma 2, sucesso e falha real são indistinguíveis sem perguntar de volta à fonte de verdade. É o que torna o resto deste desenho possível.

### Arquitetura: caminho feliz síncrono, caminho lento em background

- **caminho feliz** (chamada externa dá resposta definitiva — sucesso ou falha de negócio): síncrono, direto no handler do `sagas-common` — sem delay, sem task.
- desfechos do handler no framework SAGAS (`ResultadoHandler`, ver [saga-choreography.md](saga-choreography.md)):
  - erros "tratados" (de negócio) com ack + publica para trás — pula a DLQ, que fica só pra falha sistêmica/inesperada
  - "ack" puro para retentativa em caso de falha em *obter resposta* do serviço externo — obrigatório: `basicQos(1)` faz segurar sem ack travar a instância consumidora inteira
- status de intenção + task de retentativa (não redelivery do RabbitMQ)
  - status de intenção + contador de tentativas em `Reserva` (nomes em aberto), transição idempotente (`WHERE` aceita estado anterior ou já-no-alvo)
  - idempotente
  - limite de vezes por reserva — sobre tentativas de *obter resposta* do `consultar` (axioma 3 acima), não de `confirmar`
  - também realiza tarefas de fila
    - chama `consultar` pra saber o estado real (seguro de repetir — leitura pura)
    - sequência SAGAS para frente (sucesso) ou para trás (falha de negócio), via `Messaging.publicar()` — só o primitivo de publish, o loop de ack/nack de `iniciarConsumo` não serve aqui
    - publica *antes* de marcar status terminal — se a escrita cair no meio, o próximo tick refaz com segurança; pior caso é mensagem duplicada, downstream já tolera at-least-once (ordem inversa exigiria um segundo scan estilo outbox)
    - quando estoura o limite de tentativas de *obter resposta*: publica mensagem corrente na DLQ manualmente (`basicPublish` direto, sem delivery tag pra `nack`) + publica para trás
      - *esse* é o único momento em que faz sentido para a gente jogar o cara para a DLQ

**Desfazer reserva já confirmada, independente dos axiomas acima:** `remover` só age em reserva não confirmada (`WHERE confirmado = false`). O caso "DESFACA chega numa `Reserva` já `RESERVADA`, reverte pra pré-" (ver "Discard vs. reverter" acima) precisa de uma operação própria em `reservas-externo` (`desconfirmar`/estorno) pra funcionar ponta a ponta.

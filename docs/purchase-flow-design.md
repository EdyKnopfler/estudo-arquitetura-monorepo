# Fluxo de compra — desenho

Resultado de uma sessão de arquitetura (2026-08-02) sobre como `sessaocompra` amarra pré-reservas, pagamento e a SAGA. Complementa [saga-choreography.md](saga-choreography.md) (mecânica de fila). Diagramas (estados da sessão e SAGA): [README](../README.md#como-funciona).

## Interação do usuário

Mecanismo em [SessaoCompraController](../sessaocompra/src/main/java/com/derso/arquitetura/sessaocompra/app/SessaoCompraController.java)/[SessaoCompraService](../sessaocompra/src/main/java/com/derso/arquitetura/sessaocompra/SessaoCompraService.java). Decisões de negócio por trás:

1. `idCliente` vem do JWT só na criação da sessão, nunca de update posterior. Cliente pode ter **múltiplas sessões simultâneas** (decisão deliberada, não uma-por-cliente); ownership por sessão via `@PreAuthorize` (ver [security-and-auth.md](security-and-auth.md)).
2. Re-seleção de item já escolhido faz troca de verdade (não cria e ignora a antiga): adquire a nova antes de liberar a antiga, nunca ao contrário — evita deixar o cliente sem nada se a nova falhar. Liberação da antiga é melhor esforço; o timeout do próprio `reservas-externo` é a rede de segurança.
3. `sessaocompra` é o único ponto de contato do front com o nosso backend ("porteiro") — front nunca fala direto com `reservas-interno`/`pagamento-interno`, nem sabe os ids de reserva.
   - fora do backend, o front só acessa a URL de pagamento do gateway (ver [Criação do pagamento](#criação-do-pagamento))

## Dois timeouts

- **timeout da sessão** (`TimeoutTask`): sessão abandonada → cancela e **desfaz as pré-reservas efetuadas**.
  - `INICIADA` (pendente de criar pagamento): prazo pra escolher e avançar
    - voltar pra `INICIADA` (voltar ao pedido, reversão da SAGA) reinicia o prazo, limitado ao teto: o menor entre agora + prazo de `INICIADA` e início + teto
    - por isso o prazo de `INICIADA` tem coluna própria (`prazo_iniciada`); `start_time` fica como base do teto
  - `CRIANDO_PAGAMENTO`/`EFETUANDO_PAGAMENTO`/`PAGAMENTO_RECUSADO`: tolerância bem maior, só pra limpar sessão largada — o pagamento em si é do timeout do pagamento
  - `PAGAMENTO_EFETUADO` fica fora: a SAGA está rodando — por isso o webhook marca a sessão antes de disparar a SAGA ([Webhook do pagamento](#webhook-do-pagamento))
  - serviços dão erro e nem o cancelamento consegue seguir → `FALHA_CANCELAMENTO`, terminal — registra o caso em vez de perdê-lo
- **timeout do pagamento** (`TimeoutPagamentoTask`, em `pagamento-interno`): tempo pro cliente pagar — janela própria, alinhada à validade do meio de pagamento (PIX/redirect de gateway)
  - 15 min: com os 15 de `INICIADA`, a sessão completa (escolher + pagar) dura meia hora
  - estourou → mesma operação do [cancelamento](#cancelamento-e-prazo--gateway-como-juiz): pede cancelamento ao gateway; já pago segue o fluxo
  - não mexe na sessão: o próximo avanço abre tentativa nova
- relógio: a cada URL nova, link e timeout do pagamento zeram juntos, cada um com seu prazo
- pré-reserva no externo dura mais que escolher + pagar (prazo de `INICIADA` + timeout do pagamento) — senão a confirmação falha numa venda feita no prazo
  - a limpeza não depende disso: pode esperar o quanto quisermos
- teto: o prazo documentado da pré-reserva no `-externo`, contado desde o início da sessão ([premissa](reconciliation.md#divisão-de-papéis))
  - conservador: cada pré-reserva nasce depois do início (e uma troca gera outra), sem acompanhar uma a uma
  - renovações e retentativas cabem dentro dele; nada estende além
  - o avanço sempre devolve o prazo ao usuário, calculado por `sessaocompra` (dona do início) — o aviso de risco é do front
    - caminho normal: início + prazo de `INICIADA` + timeout do pagamento (meia hora); a folga até o teto é pras extensões
    - extensão que passa disso: o prazo informado vira o dela, sempre limitado ao teto
  - abaixo da margem de renovação, o pagamento é criado mesmo assim: o usuário decide; pago e não confirmado, a compensação estorna

Cada timeout compete com a mudança de estado pelo mesmo tipo de update condicional guardado por `status` (`WHERE status = '...'`) — em `sessaocompra`, `iniciarPagamento`/`marcarLoteComoCancelando`: quem mudar o status primeiro no banco "vence"; o outro não encontra mais linha pra afetar.

## Criação do pagamento

Desenho de 2026-09-27.

### Cadeia idempotente

front → `sessaocompra` (`iniciando-pagamento`) → `pagamento-interno` (`PUT /pagamentos/{idSessao}`) → gateway (criar)

- em cada salto, retentar = "consultar ou gerar": se a tentativa anterior gerou algo, a retentativa devolve isso; senão, gera
- nenhum salto reverte o próprio estado ao falhar — quem chamou só retenta
- nenhum salto retenta sozinho (nem na requisição, nem por job): quem dispara retentativa é o front
  - gerar outra transação quando o gateway devolve uma expirada não é retentativa, é reconciliação ([abaixo](#pagamento-da-sessão-tentativa-trocável))
  - sessão volta pra `INICIADA` só por cancelamento explícito ([abaixo](#cancelamento-e-prazo--gateway-como-juiz)) — do usuário ou pedido pela [compensação da SAGA](#saga-estendida--sessaocompra-como-bookend-do-anel)
- `pagamento-interno` é o guardião da URL do nosso lado: `sessaocompra` não guarda, repete o `PUT` a cada `iniciando-pagamento`
  - vale com a sessão em `CRIANDO_PAGAMENTO`, `EFETUANDO_PAGAMENTO` (o front pode ter perdido a resposta) ou `PAGAMENTO_RECUSADO` (volta pra `EFETUANDO_PAGAMENTO`)
- URL obtida só é substituída depois de cancelada ou vencida no gateway

### Premissas do gateway simulado (`pagamento-externo`)

Imita gateways reais no que importa pro backend — o front é abstraído, não há tela. Simples e ruim de propósito: resiliência e reconciliação ficam do nosso lado ([reconciliation.md](reconciliation.md)).

- criar e pagar são endpoints separados
  - criar: chamado por `pagamento-interno` (client-id/secret), devolve `idTransacao`, URL de pagamento fictícia e vencimento
    - só cria se o par (chave de idempotência, cliente) não existe; se existe, devolve o estado atual da transação
    - falha de propósito (chaos): falhou em criar — não existe "recusa" na criação
      - antes ou depois de gravar; depois é a falha ambígua (transação criada, resposta perdida), que o chamador resolve repetindo a chave
  - pagar: simula o usuário na tela do gateway — chamado por quem tem a URL, sorteia o desfecho (aceito ou recusado) e chama o webhook de forma síncrona
    - síncrono: a simulação mais simples, por enquanto
    - marca pago e avisa sem outbox (um gateway real precisaria): webhook que falha só vai pro log, a transação continua paga e quem pagou recebe sucesso — o nosso lado descobre pelo consultar; o dual-write bem tratado é o do [webhook no interno](#dual-write-no-webhook)
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
- a resposta do gateway completa a linha (`id_externo`, URL, vencimento, `AGUARDANDO_PAGAMENTO`); sem URL, a linha continua em `CRIANDO`
- pior caso vira "registro de algo que talvez não exista lá" (recuperável), em vez de "algo lá sem registro aqui"
- risco baixo aqui: transação perdida só custa criar outra — a antiga expira no gateway (premissas acima). Bem menos crítico que o [dual-write do webhook](#dual-write-no-webhook)

### Pagamento da sessão, tentativa trocável

- uma linha em `pagamentos` por sessão de compra: é o controle do pagamento da sessão, não uma tentativa
  - `PUT /pagamentos/{idSessao}`: repetir leva ao mesmo estado (mesmo pagamento, mesma URL)
- a tentativa é a chave de idempotência da linha (e o `id_externo` que ela rende), trocada no lugar:
  - cada tentativa nasce com uma chave nova, gravada antes da chamada
  - falha ambígua (timeout, 5xx): próxima chamada repete com a mesma chave — se a transação foi criada, o gateway devolve a mesma, sem criar outra
    - chamada ainda em andamento: a linha guarda o início da tentativa; dentro da janela (o timeout HTTP da chamada ao gateway) responde "criando, aguarde", sem chamar o gateway — só depois dela a chamada é dada como morta e a próxima repete a chave
  - gateway devolve transação expirada (chave repetida tarde, depois de uma falha ambígua): troca a chave e cria outra na mesma chamada — expirada no gateway não encerra o pagamento da sessão
  - troca de chave é update condicional (`WHERE chave = :antiga`), contra chamadas concorrentes
  - obtida a URL: toda chamada seguinte devolve a mesma, sem chamar o gateway — até ela ficar perto de vencer
  - perto de vencer: pelo vencimento devolvido pelo gateway, guardado aqui (relógios presumidos sincronizados), faltando menos que uma margem (alguns minutos)
    - cancela no gateway e abre tentativa nova
    - o cancelamento acha pago: responde "já pago" — não dispara a SAGA nem mexe na sessão; quem fecha o estado é o webhook
- a regra é "já obtivemos URL", não "já entregamos": se ela chegou ao usuário não sabemos, e não importa — devolvemos a mesma
- o webhook correlaciona por `id_externo` (passo 5 da cadeia abaixo)

### Cancelamento e prazo — gateway como juiz

Cancelar × pagar e prazo × pagar são corridas; quem decide é o gateway, fonte da verdade de "foi pago" — critério em [reconciliation.md](reconciliation.md).

- o front pode cancelar o pagamento a qualquer momento antes de pago, com ou sem URL, inclusive em `PAGAMENTO_RECUSADO` — sessão volta pra `INICIADA`
  - sem URL: nada a consultar
  - com URL: pede cancelamento ao gateway — já pago faz o pagamento vencer e seguir o fluxo
  - cancelado, o próximo `PUT` abre tentativa nova — chave nova, ids de reserva atuais
- timeout do pagamento é a mesma operação no gateway, com outro gatilho, sem mexer na sessão; nosso prazo é maior que o do gateway — quando ele dispara, a URL já venceu e a consulta é definitiva
- pagamento aceito que chega pelo webhook é reconciliado a favor da venda, mesmo fora da tentativa vigente

## Webhook do pagamento

Chega do `pagamento-externo` com `{idTransacao, status}` (`PAGO` | `RECUSADO`) e correlaciona por `id_externo` ([payload](#payload-da-mensagem-da-saga)). Primeiro objetivo: atualizar a sessão — `PAGAMENTO_EFETUADO` tira a sessão da limpeza antes da SAGA começar ([Dois timeouts](#dois-timeouts)).

- `pagamento-interno` chama `sessaocompra` por HTTP, com client-id/secret ([security-and-auth.md](security-and-auth.md#client-idsecret--serviço-a-serviço))
- webhook repetido (linha já fora de `AGUARDANDO_PAGAMENTO`): ignorado — o update condicional não acha linha; sem conciliação
- `idTransacao` sem linha (tentativa antiga, renovada): falha alto — correlação em [to-decide.md](to-decide.md)
- confirmação tardia (sessão já fora de `EFETUANDO_PAGAMENTO`/`PAGAMENTO_RECUSADO`, ex.: recolhida pela limpeza): estornar ([exemplos da reconciliação](reconciliation.md#exemplos))

### Dual-write no webhook

Pago envolve três destinos sem transação comum: a linha em `pagamentos`, a sessão e a fila da SAGA. O gateway não reenvia webhook que falhou ([premissas](#premissas-do-gateway-simulado-pagamento-externo)) — o recebimento tem que ficar gravado aqui antes de qualquer chamada de rede.

- três regras:
  - cada atividade tem um executor só (webhook ou job) — repetição só quando estritamente necessário (quem executava caiu)
  - nenhuma limpeza remove pendência que o nosso lado vai executar: a intenção só sai quando feita
  - repetição tratada com idempotência de quem recebe (o "pelo menos uma vez" que sobra)
- o webhook grava na linha, num update só: `PAGO` + as duas intenções (atualizar sessão, publicar na fila), uma coluna cada, já ocupadas por ele
  - ocupação = lease na linha, mesmo padrão de `tentativa_em` ([Pagamento da sessão](#pagamento-da-sessão-tentativa-trocável)): coluna com a hora de quem pegou; o update condicional garante que só um ganha
- e executa na hora, sessão primeiro — independentes: uma não espera a outra dar certo
  - não deixar tudo pro job: pico de pagamentos esperaria o ciclo dele pra seguir
  - cada intenção vira feita só no sucesso; a ocupação é liberada no fim, dando certo ou não
- job de pendências (papel `pendencias`, instância única): retoma só intenção livre ou com ocupação vencida (quem ocupou caiu) — nunca a que o webhook está executando
- recusa: sem intenção — perder o aviso só deixa a sessão em `EFETUANDO_PAGAMENTO`, sem efeito no fluxo

## SAGA estendida — sessaocompra como bookend do anel

A cadeia base é `pagamento → hotel → voo-ida → voo-volta` (mecânica em [saga-choreography.md](saga-choreography.md)). O desenho estende o anel com dois nós que fazem update local em `SessaoCompra`, reaproveitando o mesmo mecanismo de compensação pra trás do `sagas-common` — sem framework novo.

Diagrama: [README](../README.md#saga-disparada-a-partir-do-webhook-de-pagamento).

- **Sucesso**: webhook de pagamento marca a sessão como `PAGAMENTO_EFETUADO` e dispara o anel ([Webhook do pagamento](#webhook-do-pagamento)) → confirma em `pagamento` → `hotel` → `voo-ida` → `voo-volta` → `sessaocompra` marca `VIAGEM_RESERVADA`. Fim de cadeia.
- **Falha em qualquer etapa (inclusive em `sessaocompra: confirma`)**: propaga DESFACA pra trás até `sessaocompra: reverte` — cancelamento explícito do que já foi fechado na sessão: volta pra `INICIADA` e reinicia o prazo de `INICIADA`, limitado ao teto ([Dois timeouts](#dois-timeouts)) — dá mais tempo pro usuário escolher outra opção de voo/hotel/pagamento.
  - modelagem de status da reversão (falha de negócio × erro de execução, quando terminar em `ERRO`): em aberto
- **Recusa**: não entra no anel — nada foi confirmado, as reservas seguem em pré-reserva. O webhook leva a sessão pra `PAGAMENTO_RECUSADO`, irmão de `EFETUANDO_PAGAMENTO`:
  - não muda o fluxo — serve pra interface avisar o usuário, que tenta de novo (outro cartão, outra conta), inclusive na mesma URL, que segue valendo ([premissas](#premissas-do-gateway-simulado-pagamento-externo))
  - avançar de novo volta pra `EFETUANDO_PAGAMENTO` (mesma URL, ou renovada — ver [Criação do pagamento](#criação-do-pagamento)); pagar direto no gateway leva a `PAGAMENTO_EFETUADO` do mesmo jeito
  - não estende prazo: o tempo extra vem da renovação perto do vencimento, até o teto ([Dois timeouts](#dois-timeouts))
- **Estorno**: no `DESFACA`, o pagamento estorna no gateway e a linha em `pagamentos` volta pra tentativa nova (`CRIANDO`, chave nova) — o avanço seguinte abre outra transação
  - `ESTORNADO` só existe no gateway: com uma linha por sessão, um estado parado aqui travaria o `criarPagamento` (responderia "criando" pra sempre)
- **Discard vs. reverter**: quem detecta a falha de confirmação (ex. `voo-ida`, item não disponível mais no fornecedor) trata isso como erro local *antes* de publicar DESFACA — zera a própria pré-reserva. Quem só recebe DESFACA nunca é quem falhou (por construção da coreografia), então sempre faz a mesma ação: reverter pra pré- (`desconfirmar` no externo — simplificação em [architecture-overview.md](architecture-overview.md#reserva-genérica--simplificação-de-estudo)). Não precisa de flag na mensagem pra essa distinção — mas precisa saber qual linha local afetar (ver "Payload da mensagem da SAGA" abaixo).
- Consequência: `sessaocompra` vira a ponta final da reversão da SAGA (e do sucesso) — ganha um papel `sagas` mínimo pros dois nós e passa a depender de `sagas-common`.
- **Mecanismo de fiação — reaproveita `proximafila`/`filaanterior`, não precisa de "dois nós" de verdade.** Os dois pontos de contato de `sessaocompra` no anel colapsam numa única fila (`sessaocompra`): `proximafila: sessaocompra` em `voo-volta` e `filaanterior: sessaocompra` em `pagamento` bastam — `Messaging.iniciarConsumo` publica na próxima fila no encaminhamento pra frente e na anterior no encaminhamento pra trás (ver [saga-choreography.md](saga-choreography.md)). Um único handler em `sessaocompra`, igual aos outros, recebe as duas direções na mesma fila e decide pelo campo `tipo` (`EXECUTE` → confirma; `DESFACA` → reverte) — mesmo padrão de `ReservasSagas`/`PagamentoSagas`.

## Payload da mensagem da SAGA

Cada handler precisa agir num recurso específico (a reserva, o pagamento, a sessão) — só `tipo` + `rastreio` não dizem qual. Decisão: em vez de um id de correlação genérico + busca em cada elo, a mensagem carrega os ids internos que cada handler precisa, desde a primeira publicação no webhook.

**Só ids internos viajam na mensagem — nunca `idExterno`** (todos populados uma vez, na origem):

- `idSessaoCompra` — nós bookend de `sessaocompra` (`confirma`/`reverte`, ver "SAGA estendida" acima).
- `idPagamento` — id interno (PK) da linha em `pagamentos` (`pagamento-interno`), usado por `PagamentoSagas`.
- `idReservaHotel` — id interno (PK) da linha em `reservas`, usado por `ReservasSagas` (profile `hotel`).
- `idReservaVooIda` / `idReservaVooVolta` — mesmo papel, pro profile `voo`: um nó por perna, cada um lê o próprio campo ([cadeia](saga-choreography.md#cadeia)).

`rastreio` é o `idSessaoCompra`: correlaciona os logs de todos os nós, e a republicação pelo job de pendências mantém o mesmo.

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

1. **Cancelamento/expiração automática do lado externo é confiável.** TTL em `criar`/`confirmar` (`ReservasService`/`ReservasRepository`, `reservas-externo`); os dois são `@Transactional`, então falha explícita = zero efeito colateral. Consequência: liberar pré-reserva (cancelar/trocar) não precisa de entrega garantida — melhor esforço basta, mesmo padrão de `ReservasService.liberarMelhorEsforco` (`reservas-interno`). Não vale pra reverter reserva confirmada: confirmada não tem TTL lá.
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

**Reverter reserva confirmada:** falha ambígua no `desconfirmar` deixa a reserva confirmada sem TTL que a libere — a compensação segue pra trás mesmo assim, e o caso entra na robustez acima (`consultar` + retentativa).

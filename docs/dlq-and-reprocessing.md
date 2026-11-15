# DLQ e reprocessamento — desenho

Ainda não implementado: este doc é a fonte do desenho até virar código. Pendências em [todo.md](todo.md#dlq-e-reprocessamento), decisões em aberto em [to-decide.md](to-decide.md#dlq-e-reprocessamento). Mecânica atual do consumo/nack em [saga-choreography.md](saga-choreography.md).

## Princípio

- Na DLQ só cai erro não tratado: bug ou caso não previsto, que pede intervenção.
  - é a ferramenta pra ver o que está errado no sistema — se bem observada, guia conserto e melhoria
  - o que for "esperado" (mensagem fora de ordem, falha de negócio, estado já atingido) se trata na aplicação
- Provisório: falha persistente do externo também cai na DLQ, como "erro não tratado devidamente".
  - não é bug nosso, mas precisa de tratamento — destino definitivo em aberto ([to-decide](to-decide.md#dlq-e-reprocessamento))

## Compensação continua, a falha vai pra DLQ

- Etapa que falha ao reverter não interrompe a reversão: a mensagem vai pra DLQ da etapa e o `DESFACA` segue pra trás.
  - em `ResultadoHandler`: `NACK_DLQ` + `PARA_TRAS` — os dois eixos já são independentes, só falta usar
  - aplica nas reservas (`ReservasSagas.reverter`, hoje engole a falha com `ACK`)
  - pagamento (falha no estorno) ainda para a cadeia — em aberto

## DLQ por fila

- Cada fila da cadeia ganha a sua: `<fila>.dlq` (`hotel.dlq`, `voo-ida.dlq`…), no lugar da `errors` única.
  - custo de broker desprezível: número fixo de filas, mesmo volume de mensagens
  - ganha: contagem por etapa direto no management; reprocessar a fila inteira pela UI (move messages), sem código
  - perde: a visão cruzada das falhas de uma mesma SAGA em ordem — correlacionar pelo `rastreio`
- Armadilha na migração: redeclarar fila existente com outros `x-dead-letter-*` dá `PRECONDITION_FAILED`. No dev, apagar as filas (ou o volume do Rabbit) uma vez.

## Reprocessar só a falha

- Reprocessar não propaga: quando a falha aconteceu, a mensagem já foi encaminhada pra trás.
  - falha em vários pontos se analisa separadamente — propagar só repetiria o bug da outra etapa
  - simplifica o handler: ele não sabe que é reprocessamento
- Sinal: presença do header `x-death` → o framework força encaminhamento `NENHUM`.
  - o RabbitMQ grava o `x-death` no dead-letter; shovel/move messages preservam headers
  - o framework, ao encaminhar, publica só o body — mensagem encaminhada não carrega `x-death`

## Reprocessador

Serviço HTTP à parte, pra QA/devs/sysadmin (UI talvez depois). Operações mínimas:

- olhar a fila
- reprocessar uma mensagem
- reprocessar a fila, com limite — bugs diferentes na mesma DLQ fazem parte voltar pra lá
  - limite por quantidade: direto

Fatos do RabbitMQ que pesam no desenho:

- fila só entrega pela cabeça (`basicGet`): não dá pra escolher uma mensagem direto
- `x-death` traz fila de origem e `reason=rejected`, nunca a exceção — a causa só existe no serviço que falhou, e `basicNack` não anexa header
- shovel/move messages levam a fila inteira pra um destino só, não roteiam por header

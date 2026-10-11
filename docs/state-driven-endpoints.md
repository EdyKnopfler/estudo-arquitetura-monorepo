# Endpoints orientados a estado

Diretriz de projeto pros endpoints que movem o fluxo de compra entre etapas. Aplica o [critério de reconciliação](reconciliation.md) na forma de cada endpoint.

## Diretriz

- quem chama manda uma **intenção** (avançar rumo ao pagamento, voltar ao pedido), não uma operação ("crie", "cancele")
- quem recebe decide a ação pelo próprio estado + o estado de quem está à frente — pergunta "o que vem agora?"
  - só o lado de lá sabe se uma operação ainda é válida
- a resposta é o estado / a próxima etapa, não o "deu certo ou não" de uma operação
  - ex.: "já pago" não é erro do pedido de link — é a etapa atual
- vale pra cada caminho que o estado permite (2 ou 10, tanto faz): cada um é um endpoint desses
- repetir a mesma intenção converge — retentativa sai de graça, sem tratamento à parte

## Como evoluir

- implementar só o caminho feliz
- combinação de estados não tratada **falha alto** (exceção explícita, log, 5xx) — nunca default silencioso
- caso fora da curva entra quando aparece, um por vez, como item no todo do endpoint
- não desenhar todos os casos antes de implementar

## Por quê

Num sistema pulverizado, as combinações de estado entre os lados (F5, abas, resposta perdida, webhook atrasado, expiração) não acabam. Antecipar todas antes de implementar trava o desenho; concentrar a decisão em quem recebe deixa cada caso como uma linha a mais, coberta quando chegar a vez dela.

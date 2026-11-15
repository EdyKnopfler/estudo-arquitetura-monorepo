---
name: lint-docs
description: Lint da doc — subagente isolado acha inconsistências entre docs e código; depois revisamos e corrigimos um achado por vez
---

## 1. Análise (subagente, só leitura)

Se `git status` mostrar diff pendente em `docs/`, `README.md` ou `CLAUDE.md`, avise antes de seguir: o subagente vai ler o disco como está.

Dispare um subagente `general-purpose` com contexto limpo e este pedido:

> Lint da documentação deste repo, só leitura — não edite nada.
>
> 1. Rode `python3 -I .claude/skills/lint-docs/checar-links.py .` e inclua a saída como achados "mecânico".
> 2. Leia `CLAUDE.md` (seção Escrita é o critério), `README.md`, todos os `docs/*.md`, e o código que cada doc descreve.
> 3. Procure, por categoria (`README.md` é landing page, não documentação: repete o que a doc diz, do ponto de vista de negócio — só entra em **doc × código** e **desatualizado**):
>    - **doc × código**: doc afirma algo que o código contradiz (o código é dono do *como* implementado)
>    - **duplicação**: mesmo fato detalhado em dois lugares (resumo curto + link pro dono não conta); resumo fora do dono sem link pro dono também conta
>    - **lugar errado**: estado/próximos passos fora de `todo.md`/`README.md`/`TODO` no código; decisão não confirmada que não consta no `to-decide.md`; detalhe em lista transversal (`todo`/`to-decide`) quando há doc de tópico; detalhe de *como* em doc de assunto já implementado
>    - **contradição** entre docs
>    - **detalhe que envelhece**: nome de classe/método/arquivo sem necessidade ou fora de link; lista que se lê como completa mas é exemplo; número ou valor que o código já define
>    - **desatualizado**: item do `todo.md` já feito no código; item do `to-decide.md` já decidido em outra doc
> 4. Devolva só a lista, sem preâmbulo. Por achado: categoria · certeza (fato | interpretação) · `arquivo:linha` de cada lado · uma linha do problema · uma linha de correção sugerida. Fatos primeiro.

## 2. Revisão (nesta sessão, com o usuário)

- Mostre primeiro só a contagem por categoria e certeza.
- Depois um achado por mensagem: apresente e espere. Ele confirma, ajusta ou descarta; só então edite.
- Correção que esbarre em decisão não tomada: não decida — vira item no `to-decide.md`, se ele quiser.
- No fim: resumo do que mudou e do que foi descartado.

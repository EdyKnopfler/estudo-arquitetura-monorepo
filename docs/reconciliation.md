# Reconciliação de estado

Critério que orienta as decisões de fluxo entre os serviços internos e os `-externo`. O que falta implementar: [todo.md](todo.md).

## Critério

- o estado do nosso lado é presumido; a fonte da verdade é o serviço externo
  - pré-reservas (`reservas-externo`) e pagamento (`pagamento-externo`): só lá se sabe se ainda vale, expirou ou foi confirmado
- requisições vão chegar em ordem esdrúxula (F5 do usuário, webhook atrasado, queda no meio): conflito de status se resolve pelo estado **atual**, consultado no externo
- se do lado de lá está o que queremos (negócio fechado), reconciliamos a partir disso, em vez de executar à risca a ação que íamos fazer
  - não cancelar evento favorável ao negócio que vai virar venda

## Divisão de papéis

- simuladores `-externo`: simples, até desleixados — ver [premissas do gateway simulado](purchase-flow-design.md#premissas-do-gateway-simulado-pagamento-externo)
- resiliência e reconciliação: do nosso lado

## Exemplos

- pago no gateway de pagamento, webhook não chegou, nosso timeout ou cancelamento dispara: consulta, encontra pago, segue a SAGA em vez de cancelar
- webhook atrasado × expiração do nosso lado: quem chega primeiro trava o registro e consulta o externo
- pedido de gerar ou cancelar URL de um pagamento já pago: devolve sucesso até o front
- gateway de pagamento expira a transação antes de a URL chegar até nós: com a sessão viva, o interno gera outra — quem manda no prazo é a sessão, não o gateway
- gateway de pagamento fora do ar, pagamentos feitos sem webhook:
  - sem informação pra reconciliar — não dá pra sair cancelando sem poder cancelar lá
  - retentar enquanto fizer sentido; o limite são as pré-reservas, que também expiram
  - pagamento achado tarde dispara a SAGA; a confirmação falha na pré-reserva expirada e a compensação estorna o pagamento

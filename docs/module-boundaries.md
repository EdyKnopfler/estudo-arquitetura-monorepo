# Organização interna da regra de negócio

Até um refactor anterior, a regra de negócio de cada domínio vivia num módulo `-common` separado (biblioteca Maven, não deployável), consumido por dois artefatos deployáveis distintos (`-web` e `-sagas`) — o que acoplava build/deploy dos dois: qualquer mudança de schema ou assinatura no `-common` forçava recompilar e redeployar ambos juntos. Análise completa da época no histórico do git.

`reservas-interno` e `pagamento-interno` mantêm a regra de negócio (entidade JPA + service) direto no pacote do módulo (`entity`, `service`), sem separação em camadas — decisão avaliada, não default por omissão.

Como cada módulo já é um artefato único com dois entrypoints (REST e fila) escolhidos por profile, ver [deploy-roles-by-profile.md](deploy-roles-by-profile.md) pro mecanismo de seleção de papel.

## Camada hexagonal (domain/infrastructure) — avaliada e descartada por ora

Separar `domain` (POJOs/interfaces puros, sem Spring/JPA) de `infrastructure` (implementação JPA/HTTP) foi considerado. Decisão, depois de checar o tamanho real do código: **não vale a pena agora**. `pagamento-interno` inteiro tem uma entidade de um campo (`id`) e um service de 5 linhas; `reservas-interno` soma 163 linhas ao todo. Nesse tamanho, POJO + `@Entity` + mapper é boilerplate puro, não ganho de clareza.

Os candidatos reais a "ficar grande" seriam os fornecedores externos de verdade (hotel/voo/pagamento reais) — aqui eles são só simulados (`-externo`), então não há um segundo adapter de infraestrutura para justificar a interface ainda. Revisitar quando: (a) a regra de negócio deixar de ser trivial, ou (b) surgir necessidade real de testar a regra de negócio sem subir Spring/JPA.

## Contrato tipado para a mensagem da SAGA — ainda por fazer

A mensagem hoje é um `Map<String, Object>` genérico (`SagasMessaging`) — nenhuma entidade trafega nela, só o campo `tipo` é usado. Quando os handlers de negócio forem implementados (ver [saga-choreography.md](saga-choreography.md)), vale desenhar essa mensagem como um DTO próprio da fila (ex.: id de correlação da sessão de compra + ids de reserva), não reaproveitar a entidade JPA nem o DTO de REST — são contratos com motivos de mudança diferentes.

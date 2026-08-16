# Autenticação e segurança

Duas identidades distintas, deliberadamente separadas — não misturar ao mexer em qualquer `-web`:

## JWT — cliente final

`web-base/jwt/JwtService.java`: HMAC-SHA (`Keys.hmacShaKeyFor`, segredo via `jwt.secret`), expiração de 10 minutos, claims `id`/`email`/`userType`. Emitido por `clientes` (`AuthController`) após login. `clientes` e `sessaocompra` (profile `web`) validam esse JWT (`JwtAuthenticationFilter`, component-scan de `webbase.jwt`) — `reservas-interno` e `pagamento-interno` ainda não importam esse filtro, porque não são chamados pelo front (só client-id/secret, ver seção seguinte). `sessaocompra` é o único ponto de contato do front ("porteiro": ela mesma chama `reservas-interno` internamente, front nunca fala direto com esses serviços — ver [purchase-flow-design.md](purchase-flow-design.md)).

### Ownership por sessão — `@PreAuthorize` como aspecto

Além de autenticar o cliente, `sessaocompra` precisa garantir que a sessão de compra referenciada em cada endpoint (`/sessoes/{id}/...`) pertence a quem está autenticado — um cliente pode ter múltiplas sessões simultâneas (decisão de negócio, não técnica), então isso não pode ser "uma sessão só por cliente" implícita. Em vez de repetir essa checagem manualmente em cada método (risco real de esquecer num endpoint novo), o projeto usa Spring Security method security: `@EnableMethodSecurity` em `SecurityConfiguration` + `@PreAuthorize("@sessaoOwnership.pertence(#id, authentication)")` em cada método protegido, com `SessaoOwnership` (`sessaocompra/config`) fazendo uma checagem de existência simples (`SessaoCompraRepository.existePorIdEIdCustomer`). `TrataErros` (`web-base`, compartilhado) mapeia `AccessDeniedException` → 403.

Um teste estrutural (`SessaoCompraControllerOwnershipGuardTest`, via reflection) garante que todo método do controller com um `UUID id` de sessão no path tenha `@PreAuthorize` — quebra sozinho se alguém esquecer ao adicionar um endpoint novo.

**Nota de compilação:** `#id` no SpEL do `@PreAuthorize` depende do nome do parâmetro estar disponível em runtime — exige `<parameters>true</parameters>` no `maven-compiler-plugin` (adicionado no `pom.xml` de `sessaocompra`; os demais módulos ainda não precisam disso).

## Client-ID/Secret — serviço a serviço

`web-base/internalclient/ClientSecretAuthFilter.java`: cada `-web` mantém um mapa `client-id → client-secret` (`InternalClientsConfig`, carregado de `internal-backend.clients` no `application-<profile>.yaml`). Quem chama envia `X-Client-Id`/`X-Client-Secret` nos headers; sem match exato, 401. Usado tanto para chamadas legítimas entre serviços internos (`reservas-interno` profile `web` → `reservas-externo`, `sessaocompra` profile `web` → `reservas-interno-{hotel,voo}`) quanto para o webhook do `pagamento-externo` responder ao `pagamento-interno` profile `web`. O par `sessaocompra` → `reservas-interno-{hotel,voo}` reaproveita as credenciais que já existiam do lado de `reservas-interno` (`RESERVAS_INTERNO_WEB_HOTEL_ID/SECRET`, `RESERVAS_INTERNO_WEB_VOO_ID/SECRET`) — nenhum segredo novo foi criado.

Cada par de serviços (chamador/chamado) tem client-id/secret próprios configurados nos dois lados — ver `external-backend.*` (para quem chama) e `internal-backend.clients` (para quem aceita) em cada `application-<profile>.yaml`.

## Tratamento de erro

`web-base/config/TrataErros.java` é um `@RestControllerAdvice` global usado por todos os `-web`. Mapeia `EntityNotFoundException`→404, `BusinessException`→409, `MethodArgumentNotValidException`→400 com mensagens de campo, e qualquer outra `Exception`→500 **devolvendo `e.getMessage()` no corpo**.

## Limitações conhecidas (aceitáveis para estudo local, não levar adiante sem revisar)

- `ClientSecretAuthFilter` compara segredo com `String.equals()` — não é constant-time, então tecnicamente vulnerável a timing attack. Sem gravidade em ambiente local, mas não copiar para algo real sem trocar por comparação constant-time.
- O handler genérico de exceção devolve `e.getMessage()` cru — pode vazar detalhes internos (mensagem de SQL, etc.) para quem chama a API.

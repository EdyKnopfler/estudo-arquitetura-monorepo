# Autenticação e segurança

Duas identidades distintas, deliberadamente separadas — não misturar ao mexer em qualquer `-web`:

## JWT — cliente final

`web-base/jwt/JwtService.java`: HMAC-SHA (`Keys.hmacShaKeyFor`, segredo via `jwt.secret`), expiração de 10 minutos, claims `id`/`email`/`userType`. Emitido por `clientes` (`AuthController`) após login. `JwtAuthenticationFilter` valida em cada `-web` que atende requisição de cliente final (ex.: `sessaocompra-web`).

## Client-ID/Secret — serviço a serviço

`web-base/internalclient/ClientSecretAuthFilter.java`: cada `-web` mantém um mapa `client-id → client-secret` (`InternalClientsConfig`, carregado de `internal-backend.clients` no `application-<profile>.yaml`). Quem chama envia `X-Client-Id`/`X-Client-Secret` nos headers; sem match exato, 401. Usado tanto para chamadas legítimas entre serviços internos (`reservas-interno-web` → `reservas-externo`) quanto para o webhook do `pagamento-externo` responder ao `pagamento-interno-web`.

Cada par de serviços (chamador/chamado) tem client-id/secret próprios configurados nos dois lados — ver `external-backend.*` (para quem chama) e `internal-backend.clients` (para quem aceita) em cada `application-<profile>.yaml`.

## Tratamento de erro

`web-base/config/TrataErros.java` é um `@RestControllerAdvice` global usado por todos os `-web`. Mapeia `EntityNotFoundException`→404, `BusinessException`→409, `MethodArgumentNotValidException`→400 com mensagens de campo, e qualquer outra `Exception`→500 **devolvendo `e.getMessage()` no corpo**.

## Limitações conhecidas (aceitáveis para estudo local, não levar adiante sem revisar)

- `ClientSecretAuthFilter` compara segredo com `String.equals()` — não é constant-time, então tecnicamente vulnerável a timing attack. Sem gravidade em ambiente local, mas não copiar para algo real sem trocar por comparação constant-time.
- O handler genérico de exceção devolve `e.getMessage()` cru — pode vazar detalhes internos (mensagem de SQL, etc.) para quem chama a API.
- `.env` com credenciais de dev está commitado no repositório (`git ls-files` confirma). São segredos descartáveis (`tanto_faz_so_pra_dev`), mas o ideal a partir de agora é versionar só um `.env.example` e manter `.env` real fora do git.

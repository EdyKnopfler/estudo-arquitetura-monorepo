# Autenticação e segurança

Duas identidades distintas, deliberadamente separadas — não misturar ao mexer em qualquer `-web`:

## JWT — cliente final

`web-base/jwt/JwtIssuerService.java`+`JwtValidatorService.java`: RSA assimétrico (RS256), expiração de 10 minutos, claims `id`/`email`/`userType`/`iss`, `kid` no header. Emitido por `clientes` (`AuthController`) após login. `clientes` e `sessaocompra` (profile `web`) validam esse JWT (`JwtAuthenticationFilter`, ligado pela autoconfiguração do `web-base` com `security.auth-type: jwt`, ver [web-base-hardening.md](web-base-hardening.md)) — `reservas-interno` e `pagamento-interno` não usam JWT, porque não são chamados pelo front (só client-id/secret, ver seção seguinte). `sessaocompra` é o único ponto de contato do front com o backend ("porteiro": ela mesma chama `reservas-interno` internamente, front nunca fala direto com esses serviços — ver [purchase-flow-design.md](purchase-flow-design.md)).

**Validação não assume "é tudo meu, confio"**: `JwtValidatorService` resolve a chave pelo `kid` do header (parte do que é assinado — um `kid` forjado só faz a verificação falhar contra a chave errada) via `TrustedJwtIssuersConfig` (`jwt.trusted-issuers`, uma lista de `{kid, issuer, public-key}` por serviço), e só aceita o token se o `iss` do payload bater com o emissor esperado *para aquele kid específico* — pega até o caso de token assinado pela chave certa mas alegando ser de outro emissor. O emissor é `clientes`; o design suporta múltiplas chaves/emissores confiados sem mudar código, só config. `aud` foi deliberadamente deixado de fora: `clientes` e `sessaocompra` validam o mesmo token por design (não é confusão a fechar) — ver [web-base-hardening.md](web-base-hardening.md#2-jwt-issaud-kid).

### Gerar o par de chaves local

Cada clone gera o seu, não reaproveita o de outro:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem  # PKCS#8
openssl pkey -in private.pem -pubout -out public.pem                           # X.509
grep -v -- '-----' private.pem | tr -d '\n'   # valor de JWT_PRIVATE_KEY em .env.clientes
grep -v -- '-----' public.pem  | tr -d '\n'   # valor de JWT_PUBLIC_KEY em .env
```

Os dois valores são base64 numa linha só, sem os headers PEM.

### Ownership por sessão — `@PreAuthorize` como aspecto

Além de autenticar o cliente, `sessaocompra` precisa garantir que a sessão de compra referenciada em cada endpoint (`/sessoes/{id}/...`) pertence a quem está autenticado — um cliente pode ter múltiplas sessões simultâneas (decisão de negócio, não técnica), então isso não pode ser "uma sessão só por cliente" implícita. Em vez de repetir essa checagem manualmente em cada método (risco real de esquecer num endpoint novo), o projeto usa Spring Security method security: `@EnableMethodSecurity` em `SecurityConfiguration` + `@PreAuthorize("@sessaoOwnership.pertence(#id, authentication)")` em cada método protegido, com `SessaoOwnership` (`sessaocompra/config`) fazendo uma checagem de existência simples (`SessaoCompraRepository.existsByIdAndIdCustomer`). `TrataErros` (`web-base`, compartilhado) mapeia `AccessDeniedException` → 403.

Um teste estrutural (`SessaoCompraControllerOwnershipGuardTest`, via reflection) garante que todo método do controller com um `UUID id` de sessão no path tenha `@PreAuthorize` — quebra sozinho se alguém esquecer ao adicionar um endpoint novo.

**Nota de compilação:** `#id` no SpEL do `@PreAuthorize` depende do nome do parâmetro estar disponível em runtime — exige `<parameters>true</parameters>` no `maven-compiler-plugin` do módulo.

## Client-ID/Secret — serviço a serviço

[ClientSecretAuthFilter](../web-base/src/main/java/com/derso/arquitetura/webbase/internalclient/ClientSecretAuthFilter.java): quem chama envia `X-Client-Id`/`X-Client-Secret`; sem match exato, 401.

- cada par de serviços (chamador/chamado) tem client-id/secret próprios, configurados nos dois lados
- vale também pro webhook do `pagamento-externo` chamando de volta o `pagamento-interno`
- exceção: pagar no `pagamento-externo` não autentica — a URL de pagamento é a credencial ([purchase-flow-design.md](purchase-flow-design.md#premissas-do-gateway-simulado-pagamento-externo))

## Tratamento de erro

`@RestControllerAdvice` globais do `web-base`, usados por todos os `-web` — mapeamento exceção → status em [TrataErros.java](../web-base/src/main/java/com/derso/arquitetura/webbase/config/TrataErros.java) e [TrataErrosDeBanco.java](../web-base/src/main/java/com/derso/arquitetura/webbase/config/TrataErrosDeBanco.java). Por que o 500 devolve mensagem genérica: [web-base-hardening.md](web-base-hardening.md#tratamento-de-erro-trataerros).

## Limitações conhecidas (aceitáveis para estudo local, não levar adiante sem revisar)

- Client-secret sem rate limit — `ClientSecretAuthFilter` responde 401 sem nenhum limite de tentativas por IP/client-id. Fora de escopo do `web-base` (é infra de borda, não lógica de autenticação em si).
- `aud` não é validado no JWT — `clientes` e `sessaocompra` aceitam o mesmo token por design (ver seção JWT acima), então não é uma lacuna ativa, mas também não há proteção caso surja um segundo tipo de token com público-alvo diferente.

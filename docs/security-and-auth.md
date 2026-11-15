# Autenticação e segurança

Duas identidades distintas, deliberadamente separadas — não misturar ao mexer em qualquer serviço web (HTTP):

## JWT — cliente final

RSA assimétrico (RS256), emitido por `clientes` no login ([JwtIssuerService](../web-base/src/main/java/com/derso/arquitetura/webbase/jwt/JwtIssuerService.java)). Só `clientes` e `sessaocompra` validam ([JwtValidatorService](../web-base/src/main/java/com/derso/arquitetura/webbase/jwt/JwtValidatorService.java)): `sessaocompra` é o único ponto de contato do front ("porteiro", [desenho](purchase-flow-design.md#interação-do-usuário)); `reservas-interno` e `pagamento-interno` não são chamados pelo front e usam só client-id/secret (seção seguinte). Ligação pela autoconfiguração do `web-base`: [web-base-hardening.md](web-base-hardening.md).

Validação por `kid` + `iss` por emissor confiado — decisões em [web-base-hardening.md](web-base-hardening.md#autenticação-jwt--cliente-final).

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
- serviço com as duas identidades: `security.auth-type` é a padrão; rota com a outra o módulo declara em código (`customFilters`), e cada rota conhece uma identidade só
  - filtros criados dentro do `web-base`, não como `@Bean`: o Boot aplica todo bean `Filter` a todas as rotas, por fora do Spring Security
  - hoje: `sessaocompra` aceita client-id/secret em `/servico/**`, pro webhook de `pagamento-interno` ([purchase-flow-design.md](purchase-flow-design.md#webhook-do-pagamento))
- exceção: pagar no `pagamento-externo` não autentica — a URL de pagamento é a credencial ([purchase-flow-design.md](purchase-flow-design.md#premissas-do-gateway-simulado-pagamento-externo))

## Credenciais de dev

O `.env` (e `.env.clientes`) é a fonte única das credenciais de dev — senhas de banco/broker, client-secrets, chaves JWT.

- fora do git: cada clone cria o seu a partir do `.example`, que só lista as variáveis, com os segredos vazios
- sem default no código: yaml referencia só `${VAR}`, e teste lê do ambiente ([CredenciaisDev](../web-base/src/test/java/com/derso/arquitetura/webbase/teste/CredenciaisDev.java)) — falta de `.env` falha alto em vez de cair num valor fixo
  - motivo: default versionado é credencial no git, que os scanners apontam (já aconteceu com a chave JWT privada)
- chaves JWT fogem do padrão `<SERVIÇO>_ID`/`_SECRET`: são infra compartilhada (tipo `DB_HOST`), não credencial de um par
- mesmo esquema pro `databases.sql` (cria o usuário do banco com senha): versionado só o `databases.sql.example`

## Tratamento de erro

`@RestControllerAdvice` globais do `web-base`, usados por todos os serviços web (HTTP) — mapeamento exceção → status em [TrataErros.java](../web-base/src/main/java/com/derso/arquitetura/webbase/config/TrataErros.java) e [TrataErrosDeBanco.java](../web-base/src/main/java/com/derso/arquitetura/webbase/config/TrataErrosDeBanco.java). Por que o 500 devolve mensagem genérica: [web-base-hardening.md](web-base-hardening.md#tratamento-de-erro).

## Limitações conhecidas

Aceitáveis para estudo local, não levar adiante sem revisar:

- client-secret sem rate limit — [por quê](web-base-hardening.md#client-idsecret--serviço-a-serviço)
- `aud` não validado no JWT — [por quê](web-base-hardening.md#autenticação-jwt--cliente-final)
- JDWP exposto em todo serviço, pra attach de debugger — sem autenticação (debugger anexado = RCE); não pode existir fora de `localhost`
- secrets em `.env`/`.env.<serviço>` sem gestão real (vault, secrets manager) — são credenciais de dev descartáveis
- sem TLS em nenhuma comunicação (front↔serviços, serviço↔serviço, app↔Postgres/RabbitMQ)
- sem rate limit/lockout em `/login` — fica pra borda (gateway/WAF): throttling por IP não precisa entender o payload, sai mais barato antes da app e ajusta sem redeploy
  - reavaliar se precisar de lockout por `email` (semântica de negócio que a borda não enxerga)

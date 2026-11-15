# `web-base`: decisões de segurança

`web-base` centraliza toda a mecânica de segurança dos serviços web (HTTP) — autenticação (JWT ou client-secret), montagem da `SecurityFilterChain` e tratamento de erro. Entra por autoconfiguração, descoberta pelo classpath, não por `@ComponentScan` manual ([por quê](#wiring-da-securityfilterchain)).

## Wiring da `SecurityFilterChain`

Uma autoconfiguração só monta a filter chain pra qualquer serviço web ([WebSecurityAutoConfiguration](../web-base/src/main/java/com/derso/arquitetura/webbase/security/WebSecurityAutoConfiguration.java)); cada serviço escolhe o tipo de auth por config (`security.auth-type`).

Rota pública é a única parte que **não** é config — é decisão de código do endpoint: não muda entre dev/prod, e uma string de YAML não tem checagem de tipo. Quem precisar declara um bean com as suas ([RotaPublica](../web-base/src/main/java/com/derso/arquitetura/webbase/security/RotaPublica.java)); sem ele, nenhuma.

**Por que `@ConditionalOnWebApplication(type = SERVLET)` em vez de `@Profile("web")`**: nos papéis sem web (`web-application-type: none`), a filter chain não deve existir. Reagir ao tipo real da aplicação é mais robusto que depender de todo serviço nomear seu profile web como `"web"` — não exige lembrar de replicar uma convenção de nome.

**Por que `@AutoConfiguration` em vez de `@ComponentScan` manual**: antes, cada serviço listava à mão os pacotes do `web-base` no `@ComponentScan`. Esquecer isso ao criar um serviço novo fazia o filtro simplesmente não entrar — sem erro de compilação, sem warning, serviço sobe "funcionando" sem proteção nenhuma. `@AutoConfiguration` elimina essa dependência de lembrar: é descoberto automaticamente por qualquer serviço web (HTTP) que tenha `web-base` no classpath.

**Duas armadilhas do Spring** que moldaram o código final (só apareceram rodando de verdade, não no `compile`):
- `@Configuration.enforceUniqueMethods` (default `true`) rejeita dois `@Bean` com o mesmo nome de método mesmo sendo mutuamente exclusivos por `@ConditionalOnProperty` — por isso os dois métodos candidatos a filtro de auth (`jwt` vs `client-secret`) têm nomes de método diferentes.
- `Filter` é tipo comum demais pra injeção por tipo — o Boot já registra vários (ex.: `requestContextFilter`, `characterEncodingFilter`). Por isso `@Bean("authFilter")` explícito nos dois candidatos + `@Qualifier("authFilter")` no ponto de injeção.

## Autenticação JWT — cliente final

RSA (RS256), não HMAC — a chave privada só existe no emissor (`clientes`).

**Validação não assume "é tudo meu, confio"**: a chave é resolvida pelo `kid` do header — assinado junto com o payload, então um `kid` forjado só derruba a verificação contra a chave errada — e só depois se confere se o `iss` do payload é o emissor esperado *para aquele kid específico*. Isso pega até o caso de token assinado pela chave certa mas alegando ser de outro emissor. Os emissores confiados são uma lista em config ([TrustedJwtIssuersConfig](../web-base/src/main/java/com/derso/arquitetura/webbase/jwt/TrustedJwtIssuersConfig.java)): suporta vários sem mudar código.

**`aud` foi deixado de fora, de propósito**: `clientes` e `sessaocompra` validam o mesmo token (login do cliente final) por design — não é confusão a corrigir, é o comportamento pretendido. Um `aud` fixo que os dois checam não fecharia lacuna real, só burocracia. Reavaliar se surgir um segundo tipo de token com público-alvo diferente.

**Alg confusion e `alg: none`**: a chave de verificação resolvida pelo `kid` é uma `PublicKey` RSA, incompatível de tipo com HMAC — um token forjado declarando `HS256` e "assinado" com a chave pública como segredo é rejeitado por incompatibilidade de tipo, não por comparação de conteúdo. Token sem assinatura (`alg: none`) também é rejeitado. Os dois casos têm teste dedicado ([JwtValidatorServiceTest](../web-base/src/test/java/com/derso/arquitetura/webbase/jwt/JwtValidatorServiceTest.java)), não é só confiança na lib.

**Clock skew**: sem leeway — tolerância zero na expiração. Serviços rodam na mesma rede Docker, sem cenário real de relógio desalinhado; configurar leeway seria complexidade sem sintoma pra resolver.

**Chave RSA vazia ou malformada**: falha no boot com mensagem que distingue "propriedade presente mas vazia" de "conteúdo inválido" — `@ConditionalOnProperty` só olha se a propriedade existe, não se tem conteúdo, então essa checagem explícita evita um erro genérico de parse mais tarde.

## Client-ID/Secret — serviço a serviço

O secret recebido é comparado em tempo constante, não com `String.equals()` — evita vazar, por tempo de resposta, quantos bytes do secret o atacante acertou ([ClientSecretAuthFilter](../web-base/src/main/java/com/derso/arquitetura/webbase/internalclient/ClientSecretAuthFilter.java)).

Dois `client-id` iguais na config falham no boot — evita um sobrescrever o outro silenciosamente ([InternalClientsConfig](../web-base/src/main/java/com/derso/arquitetura/webbase/internalclient/InternalClientsConfig.java)).

**Gaps aceitos, não esquecidos**:
- Sem rate limit em tentativas de client-secret — é infra de borda, não lógica de autenticação em si; fora do escopo do `web-base`.
- O 401 de requisição não autenticada não tem o corpo de erro padrão — vem do entry point da filter chain, antes do Spring MVC (e do `@RestControllerAdvice`) entrarem em jogo, então não reusa o [tratamento de erro](#tratamento-de-erro).
- Hash em repouso dos secrets (ex.: SHA-256) fica de fora — sem ganho real enquanto todos os segredos vivem juntos no mesmo `.env` compartilhado; secrets são de alta entropia, então nem seria o caso de usar BCrypt.

## Tratamento de erro

Todos os handlers do `@RestControllerAdvice` devolvem o mesmo corpo (`{error, message}`) — contrato único, nenhum devolve `String` cru ([TrataErros](../web-base/src/main/java/com/derso/arquitetura/webbase/config/TrataErros.java)).

O handler genérico (qualquer `Exception` não mapeada) loga e devolve uma mensagem genérica ao cliente — **não** `e.getMessage()`. Uma exceção não mapeada é imprevista (bug, falha de infra); a mensagem dela pode conter detalhe interno (erro de SQL, path de arquivo) que não deve vazar pra fora. Os outros handlers devolvem a mensagem de propósito — são exceções que a própria aplicação lança com texto pensado pro cliente ler.

## Referências

- Checklist técnico item a item (o que foi testado e onde): [`web-base/src/test/`](../web-base/src/test/).
- [security-and-auth.md](security-and-auth.md) — visão geral de autenticação no projeto (as duas identidades, JWT vs client-id/secret).

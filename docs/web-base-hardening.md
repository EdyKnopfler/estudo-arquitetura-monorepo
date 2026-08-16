# `web-base`: decisões de segurança

`web-base` centraliza toda a mecânica de segurança dos 6 serviços `-web` — autenticação (JWT ou client-secret), montagem da `SecurityFilterChain` e tratamento de erro. Descoberto automaticamente via `@AutoConfiguration` (`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`), não `@ComponentScan` manual. Coberto por 37 testes (`web-base/src/test`, JUnit+Mockito puro, sem contexto Spring).

## Wiring da `SecurityFilterChain`

Um único `WebSecurityAutoConfiguration` monta a filter chain pra qualquer `-web`; cada serviço escolhe o tipo de auth por config:

```yaml
security:
  auth-type: jwt          # ou client-secret
```

Rota pública é a única parte que **não** é config — é decisão de código do endpoint (não muda entre dev/prod, e uma string de YAML não tem checagem de tipo). Quem precisar declara um bean no próprio `SecurityConfiguration.java`:

```java
@Bean
public List<RotaPublica> rotasPublicas() {
    return List.of(new RotaPublica(HttpMethod.POST, "/login"));
}
```

Sem esse bean, `WebSecurityAutoConfiguration` cai num default vazio (`@ConditionalOnMissingBean(name = "rotasPublicas")`) — hoje só `clientes` declara (`/login`, `POST /clientes`); os outros 5 não precisam de nada.

`pagamento-externo`, `pagamento-interno`, `reservas-interno`, `reservas-externo` não têm `SecurityConfiguration.java` próprio — não sobrou nada específico deles nesse assunto. `clientes` mantém um arquivo mínimo só pro `PasswordEncoder` (BCrypt) e as rotas públicas. `sessaocompra` mantém um mínimo só pro `@EnableMethodSecurity` (ownership de sessão, ortogonal ao tipo de auth).

**Por que `@ConditionalOnWebApplication(type = SERVLET)` em vez de `@Profile("web")`**: nos papéis `sagas`/`timeout` (`web-application-type: none`), a filter chain não deve existir. Reagir ao tipo real da aplicação é mais robusto que depender de todo serviço nomear seu profile web como `"web"` — não exige lembrar de replicar uma convenção de nome.

**Por que `@AutoConfiguration` em vez de `@ComponentScan` manual**: antes, cada `Application.java` listava à mão `com.derso.arquitetura.webbase.jwt`/`.internalclient` no `@ComponentScan`. Esquecer isso ao criar um serviço novo fazia o filtro simplesmente não entrar — sem erro de compilação, sem warning, serviço sobe "funcionando" sem proteção nenhuma. `@AutoConfiguration` elimina essa dependência de lembrar: é descoberto automaticamente por qualquer `-web` que tenha `web-base` no classpath.

**Duas armadilhas do Spring** que moldaram o código final (só apareceram rodando de verdade, não no `compile`):
- `@Configuration.enforceUniqueMethods` (default `true`) rejeita dois `@Bean` com o mesmo nome de método mesmo sendo mutuamente exclusivos por `@ConditionalOnProperty` — por isso os dois métodos candidatos a filtro de auth (`jwt` vs `client-secret`) têm nomes de método diferentes.
- `Filter` é tipo comum demais pra injeção por tipo — o Boot já registra vários (`requestContextFilter`, `characterEncodingFilter` etc.). Por isso `@Bean("authFilter")` explícito nos dois candidatos + `@Qualifier("authFilter")` no ponto de injeção.

## Autenticação JWT — cliente final

Emissor (`JwtIssuerService`) e validador (`JwtValidatorService`) usam RSA (RS256), não HMAC — chave privada só existe onde `jwt.private-key` está configurado (hoje só `clientes`).

**Validação não assume "é tudo meu, confio"**: `TrustedJwtIssuersConfig` (mesmo padrão de `InternalClientsConfig`) liga uma lista `jwt.trusted-issuers` (`kid`/`issuer`/`public-key`) a dois mapas. `JwtValidatorService` resolve a chave pelo `kid` do header — assinado junto com o payload, então um `kid` forjado só derruba a verificação contra a chave errada, não existe "confusão" possível aqui — e só depois confere se o `iss` do payload é o emissor esperado *para aquele kid específico*. Isso pega até o caso de token assinado pela chave certa mas alegando ser de outro emissor. Hoje só existe um emissor confiado (`clientes`), mas o design suporta múltiplos sem mudar código, só config.

**`aud` foi deixado de fora, de propósito**: `clientes` e `sessaocompra` validam o mesmo token (login do cliente final) por design — não é confusão a corrigir, é o comportamento pretendido. Um `aud` fixo que os dois checam não fecharia lacuna real hoje, só burocracia. Reavaliar se surgir um segundo tipo de token com público-alvo diferente.

**Alg confusion e `alg: none`**: JJWT resolve a chave de verificação pelo `kid` (retorna uma `PublicKey` RSA), e essa chave é incompatível de tipo com HMAC — um token forjado declarando `HS256` e "assinado" com a chave pública como segredo é rejeitado por incompatibilidade de tipo, não por comparação de conteúdo. Token sem assinatura (`alg: none`) também é rejeitado. Ambos os casos têm teste dedicado em `JwtValidatorServiceTest`, não é só confiança na lib.

**Clock skew**: sem leeway configurado — default do JJWT é tolerância zero na expiração. Serviços rodam na mesma rede Docker, sem cenário real de relógio desalinhado; configurar leeway seria complexidade sem sintoma pra resolver.

**Chave RSA vazia ou malformada**: falha no boot com mensagem que distingue "propriedade presente mas vazia" de "conteúdo inválido" — `@ConditionalOnProperty` só olha se a propriedade existe, não se tem conteúdo, então essa checagem explícita evita um erro genérico de parse mais tarde.

## Client-ID/Secret — serviço a serviço

`ClientSecretAuthFilter` compara o secret recebido com `MessageDigest.isEqual` (constant-time) em vez de `String.equals()` — evita vazar, por tempo de resposta, quantos bytes do secret o atacante acertou.

`InternalClientsConfig` falha no boot se dois `client-id` colidirem na mesma lista (`Collectors.toMap` sem merge function) — evita um client-id sobrescrever outro silenciosamente.

**Gaps aceitos, não esquecidos**:
- Sem rate limit em tentativas de client-secret — é infra de borda, não lógica de autenticação em si; fora do escopo do `web-base`.
- O 401 que `ClientSecretAuthFilter` devolve não tem corpo estruturado em `ErroDTO` — o filtro responde antes do Spring MVC (e do `@RestControllerAdvice`) entrarem em jogo, então não dá pra reusar o mesmo mecanismo de `TrataErros` sem reestruturar o filtro.
- Hash em repouso dos secrets (ex. SHA-256) fica de fora — sem ganho real enquanto todos os segredos vivem juntos no mesmo `.env` compartilhado; secrets são de alta entropia, então nem seria o caso de usar BCrypt.

## Tratamento de erro (`TrataErros`)

Todos os handlers do `@RestControllerAdvice` devolvem `ErroDTO` (`{error, message}`) — contrato único, nenhum devolve `String` cru.

O handler genérico (qualquer `Exception` não mapeada) loga via SLF4J e devolve uma mensagem genérica ao cliente — **não** `e.getMessage()`. Uma exceção não mapeada é imprevista (bug, falha de infra); a mensagem dela pode conter detalhe interno (erro de SQL, path de arquivo) que não deve vazar pra fora. Os outros handlers (`BusinessException`, `EntityNotFoundException`, `UsuarioInvalidoException` etc.) devolvem a mensagem de propósito — são exceções que a própria aplicação lança com texto pensado pro cliente ler.

Captura/agregação centralizada de log continua fora de escopo (`// TODO` no código) — hoje é só SLF4J local a cada instância.

## Referências

- Checklist técnico completo item-a-item (o que foi testado e onde): `web-base/src/test/`.
- `docs/security-and-auth.md` — visão geral de autenticação no projeto (as duas identidades, JWT vs client-id/secret).
- `docs/todo.md` — pendências que não são deste módulo (ex.: cobertura de teste de `sagas-common`).

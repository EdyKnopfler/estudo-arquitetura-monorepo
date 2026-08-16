package com.derso.arquitetura.webbase.jwt;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import io.jsonwebtoken.Jwts;

// Só existe como bean onde `jwt.private-key` está configurado (ver JwtAutoConfiguration) —
// nem todo serviço que valida token pode emitir um.
public class JwtIssuerService {

    private static final Duration EXPIRATION = Duration.ofMinutes(10);

    private final PrivateKey key;
    private final String kid;
    private final String issuer;

    public JwtIssuerService(String privateKeyBase64, String kid, String issuer) {
        this.key = parsePrivateKey(privateKeyBase64);
        this.kid = kid;
        this.issuer = issuer;
    }

    public String generateToken(String id, String email, String type) {
        Instant now = Instant.now();

        return Jwts.builder()
            .header().keyId(kid).and()
            .claim("id", id)
            .claim("email", email)
            .claim("userType", type)
            .issuer(issuer)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(EXPIRATION)))
            .signWith(key, Jwts.SIG.RS256)
            .compact();
    }

    private static PrivateKey parsePrivateKey(String base64) {
        // @ConditionalOnProperty só olha se a propriedade existe, não se tem conteúdo —
        // string vazia ainda ativa este bean, e sem essa checagem o erro só apareceria
        // depois, com mensagem genérica de parse de chave
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("jwt.private-key está presente mas vazia");
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new IllegalStateException("Chave privada JWT inválida", e);
        }
    }

}

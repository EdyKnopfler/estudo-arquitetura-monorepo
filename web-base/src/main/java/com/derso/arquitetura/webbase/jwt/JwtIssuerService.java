package com.derso.arquitetura.webbase.jwt;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;

// Só instanciado onde `jwt.private-key` está configurado — nem todo serviço que valida token pode emitir um.
@Service
@ConditionalOnProperty("jwt.private-key")
public class JwtIssuerService {

    private static final Duration EXPIRATION = Duration.ofMinutes(10);

    private final PrivateKey key;

    public JwtIssuerService(@Value("${jwt.private-key}") String privateKeyBase64) {
        this.key = parsePrivateKey(privateKeyBase64);
    }

    public String generateToken(String id, String email, String type) {
        Instant now = Instant.now();

        return Jwts.builder()
            .claim("id", id)
            .claim("email", email)
            .claim("userType", type)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(EXPIRATION)))
            .signWith(key, Jwts.SIG.RS256)
            .compact();
    }

    private static PrivateKey parsePrivateKey(String base64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new IllegalStateException("Chave privada JWT inválida", e);
        }
    }

}

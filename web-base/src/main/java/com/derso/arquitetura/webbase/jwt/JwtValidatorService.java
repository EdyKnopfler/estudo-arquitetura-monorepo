package com.derso.arquitetura.webbase.jwt;

import java.security.PublicKey;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

public class JwtValidatorService {

    private final Map<String, PublicKey> chavesPorKid;
    private final Map<String, String> emissorEsperadoPorKid;

    public JwtValidatorService(TrustedJwtIssuersConfig config) {
        this.chavesPorKid = config.getChavesPorKid();
        this.emissorEsperadoPorKid = config.getEmissorEsperadoPorKid();
    }

    public Optional<Claims> validateToken(String token) {
        try {
            var jws = Jwts.parser()
                .keyLocator(header -> chavesPorKid.get(header.get("kid")))
                .build()
                .parseSignedClaims(token);

            String kid = (String) jws.getHeader().get("kid");
            Claims claims = jws.getPayload();

            if (!Objects.equals(emissorEsperadoPorKid.get(kid), claims.getIssuer())) {
                return Optional.empty();
            }

            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

}

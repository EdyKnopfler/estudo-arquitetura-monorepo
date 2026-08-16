package com.derso.arquitetura.webbase.jwt;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

// Quem valida um JWT não deve confiar "porque é meu" — resolve a chave pelo `kid` do header
// (assinado, então um kid forjado só derruba a verificação contra a chave errada) e depois
// confere se o `iss` do payload é o emissor esperado para aquele kid especificamente.
@ConfigurationProperties(prefix = "jwt")
public class TrustedJwtIssuersConfig {

    private Map<String, PublicKey> chavesPorKid = new HashMap<>();
    private Map<String, String> emissorEsperadoPorKid = new HashMap<>();

    public void setTrustedIssuers(List<TrustedIssuer> trustedIssuers) {
        if (trustedIssuers == null) {
            return;
        }

        this.chavesPorKid = trustedIssuers.stream().collect(Collectors.toMap(
            TrustedIssuer::getKid,
            t -> parsePublicKey(t.getKid(), t.getPublicKey())
        ));
        this.emissorEsperadoPorKid = trustedIssuers.stream().collect(Collectors.toMap(
            TrustedIssuer::getKid,
            TrustedIssuer::getIssuer
        ));
    }

    public Map<String, PublicKey> getChavesPorKid() {
        return chavesPorKid;
    }

    public Map<String, String> getEmissorEsperadoPorKid() {
        return emissorEsperadoPorKid;
    }

    private static PublicKey parsePublicKey(String kid, String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("jwt.trusted-issuers com kid '" + kid + "' tem public-key vazia");
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new IllegalStateException("Chave pública JWT inválida (kid '" + kid + "')", e);
        }
    }

    @Setter
    @Getter
    public static class TrustedIssuer {
        private String kid;
        private String issuer;
        private String publicKey;
    }

}

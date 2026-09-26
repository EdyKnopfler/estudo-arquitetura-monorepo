package com.derso.arquitetura.clientes;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.springframework.test.context.DynamicPropertyRegistry;

// Par RSA gerado por execução: em produção a chave privada vem só de .env.clientes (env_file do compose).
public final class ChavesJwtDeTeste {

    private static final KeyPair PAR = gerar();

    private ChavesJwtDeTeste() {
    }

    // kid/issuer também: lista indexada só é lida da fonte de maior prioridade — registrar só
    // public-key deixaria o emissor confiável sem kid/issuer
    public static void registrar(DynamicPropertyRegistry registry) {
        registry.add("jwt.private-key", () -> Base64.getEncoder().encodeToString(PAR.getPrivate().getEncoded()));
        registry.add("jwt.trusted-issuers[0].kid", () -> "clientes-1");
        registry.add("jwt.trusted-issuers[0].issuer", () -> "clientes");
        registry.add("jwt.trusted-issuers[0].public-key", () -> Base64.getEncoder().encodeToString(PAR.getPublic().getEncoded()));
    }

    private static KeyPair gerar() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}

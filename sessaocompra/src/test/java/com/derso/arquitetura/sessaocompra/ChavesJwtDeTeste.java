package com.derso.arquitetura.sessaocompra;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

import com.derso.arquitetura.webbase.jwt.JwtIssuerService;

// Par RSA gerado por execução, fazendo o papel de `clientes`: sessaocompra confia na pública e o
// teste assina tokens reais com a privada. Via @Import (não @DynamicPropertySource) pra classes
// de teste com o mesmo @Import compartilharem o contexto em cache.
@TestConfiguration(proxyBeanMethods = false)
public class ChavesJwtDeTeste {

    static final String KID = "clientes-1";
    static final String EMISSOR = "clientes";

    private static final KeyPair PAR = gerar();

    // kid/issuer também: lista indexada só é lida da fonte de maior prioridade
    @Bean
    DynamicPropertyRegistrar jwtProperties() {
        return registry -> {
            registry.add("jwt.trusted-issuers[0].kid", () -> KID);
            registry.add("jwt.trusted-issuers[0].issuer", () -> EMISSOR);
            registry.add("jwt.trusted-issuers[0].public-key", () -> base64(PAR.getPublic().getEncoded()));
        };
    }

    static JwtIssuerService emissorConfiavel() {
        return emissor(PAR.getPrivate(), KID, EMISSOR);
    }

    static JwtIssuerService emissor(PrivateKey chave, String kid, String emissor) {
        return new JwtIssuerService(base64(chave.getEncoded()), kid, emissor);
    }

    static PrivateKey chavePrivadaConfiavel() {
        return PAR.getPrivate();
    }

    static KeyPair gerar() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

}

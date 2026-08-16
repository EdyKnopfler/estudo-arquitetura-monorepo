package com.derso.arquitetura.webbase.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class TrustedJwtIssuersConfigTest {

    @Test
    void listaDeEmissoresViraMapasPorKid() {
        KeyPair par = ChavesRsaDeTeste.gerarPar();
        String publicKeyBase64 = ChavesRsaDeTeste.base64PublicaDe(par);

        TrustedJwtIssuersConfig config = bind(Map.of(
            "jwt.trusted-issuers[0].kid", "k1",
            "jwt.trusted-issuers[0].issuer", "clientes",
            "jwt.trusted-issuers[0].public-key", publicKeyBase64
        ));

        assertEquals(1, config.getChavesPorKid().size());
        assertTrue(config.getChavesPorKid().containsKey("k1"));
        assertEquals("clientes", config.getEmissorEsperadoPorKid().get("k1"));
    }

    @Test
    void kidDuplicadoFalhaNoBoot() {
        KeyPair par = ChavesRsaDeTeste.gerarPar();
        String publicKeyBase64 = ChavesRsaDeTeste.base64PublicaDe(par);

        Exception e = assertThrows(Exception.class, () -> bind(Map.of(
            "jwt.trusted-issuers[0].kid", "k1",
            "jwt.trusted-issuers[0].issuer", "clientes",
            "jwt.trusted-issuers[0].public-key", publicKeyBase64,
            "jwt.trusted-issuers[1].kid", "k1",
            "jwt.trusted-issuers[1].issuer", "outro-emissor",
            "jwt.trusted-issuers[1].public-key", publicKeyBase64
        )));

        assertTrue(causaContem(e, "Duplicate key"),
            "esperava a cadeia de causas mencionar chave duplicada, mas foi: " + e);
    }

    @Test
    void publicKeyVaziaFalhaNoBootComMensagemClara() {
        Exception e = assertThrows(Exception.class, () -> bind(Map.of(
            "jwt.trusted-issuers[0].kid", "k1",
            "jwt.trusted-issuers[0].issuer", "clientes",
            "jwt.trusted-issuers[0].public-key", ""
        )));

        assertTrue(causaContem(e, "jwt.trusted-issuers com kid 'k1' tem public-key vazia"),
            "mensagem inesperada na cadeia de causas: " + e);
    }

    private static boolean causaContem(Throwable e, String trecho) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(trecho)) {
                return true;
            }
        }
        return false;
    }

    private static TrustedJwtIssuersConfig bind(Map<String, String> propriedades) {
        TrustedJwtIssuersConfig config = new TrustedJwtIssuersConfig();
        Binder binder = new Binder(new MapConfigurationPropertySource(propriedades));
        return binder.bind("jwt", Bindable.ofInstance(config)).get();
    }
}

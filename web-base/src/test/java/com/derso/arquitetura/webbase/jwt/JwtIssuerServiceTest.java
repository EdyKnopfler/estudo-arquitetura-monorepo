package com.derso.arquitetura.webbase.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;

import org.junit.jupiter.api.Test;

class JwtIssuerServiceTest {

    @Test
    void chavePrivadaVaziaFalhaNoBootComMensagemClara() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> new JwtIssuerService("", "clientes-1", "clientes"));

        assertEquals("jwt.private-key está presente mas vazia", e.getMessage());
    }

    @Test
    void chavePrivadaMalFormadaFalhaNoBootComMensagemClara() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> new JwtIssuerService("nao-e-base64-de-chave-nenhuma", "clientes-1", "clientes"));

        assertEquals("Chave privada JWT inválida", e.getMessage());
    }

    @Test
    void chavePrivadaValidaGeraTokenComTresPartes() {
        KeyPair par = ChavesRsaDeTeste.gerarPar();
        JwtIssuerService issuer = new JwtIssuerService(ChavesRsaDeTeste.base64PrivadaDe(par), "clientes-1", "clientes");

        String token = issuer.generateToken("123", "a@b.com", "CLIENTE");

        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3, "JWT deve ter header.payload.assinatura");
    }
}

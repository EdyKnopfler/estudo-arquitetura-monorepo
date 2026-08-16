package com.derso.arquitetura.webbase.jwt;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

// gera pares RSA descartáveis em memória, só para os testes não dependerem de nenhuma chave real do projeto
final class ChavesRsaDeTeste {

    private ChavesRsaDeTeste() {
    }

    static KeyPair gerarPar() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String base64PrivadaDe(KeyPair par) {
        return Base64.getEncoder().encodeToString(par.getPrivate().getEncoded());
    }

    static String base64PublicaDe(KeyPair par) {
        return Base64.getEncoder().encodeToString(par.getPublic().getEncoded());
    }
}

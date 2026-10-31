package com.derso.arquitetura.webbase.teste;

// Credenciais de dev vêm do .env (fonte da verdade, fora do git) carregado no ambiente — README, seção Testes
public final class CredenciaisDev {

    private CredenciaisDev() {
    }

    public static String de(String variavel) {
        String valor = System.getenv(variavel);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(variavel + " não definida — carregue o .env antes de rodar os testes (README, seção Testes)");
        }
        return valor;
    }

}

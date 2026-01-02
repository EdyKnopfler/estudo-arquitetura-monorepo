package com.derso.arquitetura.webbase.jwt;

public class UsuarioInvalidoException extends RuntimeException {
    public UsuarioInvalidoException() {
        super("Usuário inválido");
    }
}

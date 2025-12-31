package com.derso.arquitetura.jwt;

public class UsuarioInvalidoException extends RuntimeException {
    public UsuarioInvalidoException() {
        super("Usuário inválido");
    }
}

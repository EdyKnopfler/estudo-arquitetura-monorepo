package com.derso.arquitetura.sessaocompra.config;

public class SessaoCompraException extends RuntimeException {

    public SessaoCompraException(String message) {
        super(message);
    }

    public SessaoCompraException(String message, Throwable cause) {
        super(message, cause);
    }
}
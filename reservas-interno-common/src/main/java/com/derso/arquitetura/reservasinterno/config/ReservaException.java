package com.derso.arquitetura.reservasinterno.config;

public class ReservaException extends RuntimeException {

    public ReservaException(String message) {
        super(message);
    }

    public ReservaException(String message, Throwable cause) {
        super(message, cause);
    }
}
package com.derso.arquitetura.reservasexterno.app;

import java.util.UUID;

public class FalhaAleatoriaException extends RuntimeException {
    public FalhaAleatoriaException() {
        super("Falhou por motivo de: " + UUID.randomUUID().toString());
    }
}

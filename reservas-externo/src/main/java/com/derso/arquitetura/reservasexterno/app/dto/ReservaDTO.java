package com.derso.arquitetura.reservasexterno.app.dto;

import java.time.Instant;
import java.util.UUID;

public record ReservaDTO(
    UUID id,
    UUID idCliente,
    Instant criacao,
    boolean confirmado
) {
    
}

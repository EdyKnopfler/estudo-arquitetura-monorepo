package com.derso.arquitetura.reservasinterno.dto;

import java.util.UUID;

public record ReservaDTO(
    UUID id,
    UUID idExterno
) {
    
}

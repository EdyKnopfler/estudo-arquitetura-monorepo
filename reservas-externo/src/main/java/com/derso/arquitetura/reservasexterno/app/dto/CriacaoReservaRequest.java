package com.derso.arquitetura.reservasexterno.app.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record CriacaoReservaRequest(
    @NotNull UUID idCliente
) {
    
}

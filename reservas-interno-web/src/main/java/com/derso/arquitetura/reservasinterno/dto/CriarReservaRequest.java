package com.derso.arquitetura.reservasinterno.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record CriarReservaRequest(
    // TODO guardar os outros dados no serviço interno; por enquanto só o exigido pelo externo
    @NotNull UUID idCliente
) {
    
}
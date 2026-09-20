package com.derso.arquitetura.pagamentointerno.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record CriarPagamentoRequest(
    @NotNull UUID idSessaoCompra,
    @NotNull UUID idReservaHotel,
    @NotNull UUID idReservaVooIda,
    @NotNull UUID idReservaVooVolta
) {

}

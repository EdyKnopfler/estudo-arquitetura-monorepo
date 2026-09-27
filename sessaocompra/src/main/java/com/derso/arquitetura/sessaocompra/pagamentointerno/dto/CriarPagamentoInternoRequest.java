package com.derso.arquitetura.sessaocompra.pagamentointerno.dto;

import java.util.UUID;

public record CriarPagamentoInternoRequest(
    UUID idReservaHotel,
    UUID idReservaVooIda,
    UUID idReservaVooVolta
) {

}

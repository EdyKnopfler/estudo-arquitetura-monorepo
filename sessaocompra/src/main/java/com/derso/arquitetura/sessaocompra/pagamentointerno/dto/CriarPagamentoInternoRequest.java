package com.derso.arquitetura.sessaocompra.pagamentointerno.dto;

import java.util.UUID;

public record CriarPagamentoInternoRequest(
    UUID idSessaoCompra,
    UUID idReservaHotel,
    UUID idReservaVooIda,
    UUID idReservaVooVolta
) {

}

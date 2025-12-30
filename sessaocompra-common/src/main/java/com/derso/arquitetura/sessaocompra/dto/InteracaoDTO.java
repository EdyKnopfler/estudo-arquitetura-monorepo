package com.derso.arquitetura.sessaocompra.dto;

import java.util.UUID;

public record InteracaoDTO(
    UUID idCliente,
    UUID idReservaVooIda,
    UUID idReservaHotel,
    UUID idReservaVooVolta
) {
    
}

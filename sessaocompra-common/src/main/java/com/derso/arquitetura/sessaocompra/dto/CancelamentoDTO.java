package com.derso.arquitetura.sessaocompra.dto;

import java.util.UUID;

public record CancelamentoDTO(
    UUID id,
    UUID idReservaVooIda,
    UUID idReservaHotel,
    UUID idReservaVooVolta
) {
    
}

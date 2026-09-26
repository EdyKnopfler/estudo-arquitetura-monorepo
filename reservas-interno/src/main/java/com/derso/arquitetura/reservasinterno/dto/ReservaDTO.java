package com.derso.arquitetura.reservasinterno.dto;

import java.util.UUID;

// Só o id interno — idExterno não sai de reservas-interno (mesma regra do PagamentoDTO).
public record ReservaDTO(
    UUID id
) {

}

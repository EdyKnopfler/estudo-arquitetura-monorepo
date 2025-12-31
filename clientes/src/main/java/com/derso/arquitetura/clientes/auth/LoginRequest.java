package com.derso.arquitetura.clientes.auth;

import jakarta.validation.constraints.NotEmpty;

public record LoginRequest(
    @NotEmpty String email,
    @NotEmpty String senha
) {

}

package com.derso.arquitetura.clientes.auth;

public record LoginResponse(
    String token,
    String refreshToken
) {

}

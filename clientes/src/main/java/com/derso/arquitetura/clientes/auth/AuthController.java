package com.derso.arquitetura.clientes.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.derso.arquitetura.clientes.app.ClientesService;
import com.derso.arquitetura.webbase.jwt.JwtService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final ClientesService clientesService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest dadosLogin) {

        // TODO gerar refresh token e criar o endpoint para usá-lo
        String refreshToken = null;

        return clientesService.validarLogin(dadosLogin.email(), dadosLogin.senha())
            .map(cliente -> ResponseEntity.ok(
                new LoginResponse(jwtService.generateToken(cliente.id().toString(), cliente.email(), null), refreshToken)))
            .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
    
}

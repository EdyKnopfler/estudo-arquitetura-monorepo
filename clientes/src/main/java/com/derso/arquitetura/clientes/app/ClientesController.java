package com.derso.arquitetura.clientes.app;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.derso.arquitetura.webbase.jwt.UsuarioAutenticado;
import com.derso.arquitetura.webbase.jwt.UsuarioInvalidoException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClientesController {

    private final ClientesService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteDTO cadastrarNovo(@RequestBody @Valid ClienteDTO dados) {
        return service.criarNovo(dados);
    }

    @DeleteMapping("/{id}")
    public void cancelarInscricao(@PathVariable("id") UUID id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        if (!usuario.id().equals(id.toString())) {
            throw new UsuarioInvalidoException();
        }

        service.deletar(id);
    }

}

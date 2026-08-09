package com.derso.arquitetura.sessaocompra.config;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.derso.arquitetura.sessaocompra.SessaoCompraRepository;
import com.derso.arquitetura.webbase.jwt.UsuarioAutenticado;

import lombok.RequiredArgsConstructor;

@Component("sessaoOwnership")
@RequiredArgsConstructor
public class SessaoOwnership {

    private final SessaoCompraRepository repositorio;

    public boolean pertence(UUID id, Authentication auth) {
        UsuarioAutenticado usuario = (UsuarioAutenticado) auth.getPrincipal();
        return repositorio.existsByIdAndIdCustomer(id, UUID.fromString(usuario.id()));
    }

}

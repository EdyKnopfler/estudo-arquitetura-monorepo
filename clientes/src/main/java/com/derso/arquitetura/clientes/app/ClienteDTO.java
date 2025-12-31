package com.derso.arquitetura.clientes.app;

import java.util.UUID;

import com.derso.arquitetura.clientes.entity.Cliente;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record ClienteDTO(
    
    UUID id,

    @NotEmpty
    @Size(max = 120)
    String nome,

    @NotEmpty
    @Size(max = 150)
    String email,

    @NotEmpty
    @Size(max = 11)
    String cpf,

    @NotEmpty
    @Size(max = 60)
    String senha

) {

    public static ClienteDTO fromEntity(Cliente cliente) {
        return new ClienteDTO(cliente.getId(), cliente.getNome(), cliente.getEmail(), cliente.getCpf(), null);
    }

}

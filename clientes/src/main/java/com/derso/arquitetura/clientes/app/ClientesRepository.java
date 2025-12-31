package com.derso.arquitetura.clientes.app;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.derso.arquitetura.clientes.entity.Cliente;

public interface ClientesRepository extends JpaRepository<Cliente, UUID> {

    Optional<Cliente> findByEmail(String email);
    
}

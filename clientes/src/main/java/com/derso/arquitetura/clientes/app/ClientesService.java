package com.derso.arquitetura.clientes.app;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.derso.arquitetura.clientes.entity.Cliente;
import com.derso.arquitetura.webbase.config.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClientesService {

    private final ClientesRepository repositorio;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ClienteDTO criarNovo(ClienteDTO dados) {
        if (!repositorio.findByEmail(dados.email()).isEmpty()) {
            throw new BusinessException("E-mail já usado");
        }

        return ClienteDTO.fromEntity(
            repositorio.save(
                new Cliente(
                    UUID.randomUUID(), 
                    dados.nome(),
                    dados.email(),
                    dados.cpf(), 
                    passwordEncoder.encode(dados.senha())
                )
            )
        );
    }

    @Transactional
    public void deletar(UUID id) {
        repositorio.deleteById(id);
    }

    public Optional<ClienteDTO> validarLogin(String email, String password) {
        return repositorio.findByEmail(email)
            .filter(user -> passwordEncoder.matches(password, user.getSenha()))
            .map(ClienteDTO::fromEntity);
    }
    
}

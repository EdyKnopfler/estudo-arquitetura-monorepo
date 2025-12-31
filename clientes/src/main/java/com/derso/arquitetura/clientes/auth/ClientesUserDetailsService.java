package com.derso.arquitetura.clientes.auth;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.derso.arquitetura.clientes.app.ClientesRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClientesUserDetailsService implements UserDetailsService {

    private final ClientesRepository repositorio;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        repositorio.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("Cliente " + email + " não encontrado"));

        return User.withUsername(email).build();

    }
    
}

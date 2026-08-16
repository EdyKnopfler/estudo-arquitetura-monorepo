package com.derso.arquitetura.clientes.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.derso.arquitetura.webbase.security.RotaPublica;

// A SecurityFilterChain em si (tipo de auth, filtro) é montada em web-base
// (WebSecurityAutoConfiguration), escolhida via security.auth-type no application.yaml — este
// arquivo só sobra pro que é específico deste serviço: o encoder de senha e quais rotas são
// públicas (decisão do endpoint, não de ambiente — por isso em código, não em yaml).
@Configuration
public class SecurityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public List<RotaPublica> rotasPublicas() {
        return List.of(
            new RotaPublica(HttpMethod.POST, "/login"),
            new RotaPublica(HttpMethod.POST, "/clientes")
        );
    }

}

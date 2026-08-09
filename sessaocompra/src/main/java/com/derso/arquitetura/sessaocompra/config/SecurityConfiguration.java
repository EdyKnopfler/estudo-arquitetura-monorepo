package com.derso.arquitetura.sessaocompra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.derso.arquitetura.webbase.jwt.JwtAuthenticationFilter;

// Só o papel "web" expõe HTTP; o papel "timeout" roda com web-application-type: none.
@Configuration
@Profile("web")
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    protected DefaultSecurityFilterChain configure(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

}

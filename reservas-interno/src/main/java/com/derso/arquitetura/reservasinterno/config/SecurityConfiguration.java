package com.derso.arquitetura.reservasinterno.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.derso.arquitetura.webbase.internalclient.ClientSecretAuthFilter;

@Configuration
@Profile("web")
public class SecurityConfiguration {

    @Bean
    protected DefaultSecurityFilterChain configure(HttpSecurity http, ClientSecretAuthFilter clientSecretFilter) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .addFilterBefore(clientSecretFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    @ConfigurationProperties(prefix = "internal-client")
    public Map<String, Object> appConfigMap() {
        return new HashMap<>();
    }

}

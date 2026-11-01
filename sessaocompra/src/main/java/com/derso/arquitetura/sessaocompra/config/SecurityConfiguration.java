package com.derso.arquitetura.sessaocompra.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import com.derso.arquitetura.webbase.security.AuthType;
import com.derso.arquitetura.webbase.security.FiltroCustom;

// A SecurityFilterChain em si (tipo de auth, rotas públicas) é montada em web-base
// (WebSecurityAutoConfiguration), escolhida via security.auth-type no application.yaml — este
// arquivo só sobra pro que é específico deste serviço: method security pro
// @PreAuthorize de ownership de sessão (ver SessaoOwnership) e as rotas chamadas por outros serviços.
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public List<FiltroCustom> customFilters() {
        return List.of(new FiltroCustom("/servico/**", AuthType.CLIENT_SECRET));
    }

}

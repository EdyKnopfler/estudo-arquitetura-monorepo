package com.derso.arquitetura.sessaocompra.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

// A SecurityFilterChain em si (tipo de auth, rotas públicas) é montada em web-base
// (WebSecurityAutoConfiguration), escolhida via security.auth-type no application.yaml — este
// arquivo só sobra pro que é específico deste serviço: method security pro
// @PreAuthorize de ownership de sessão (ver SessaoOwnership).
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

}

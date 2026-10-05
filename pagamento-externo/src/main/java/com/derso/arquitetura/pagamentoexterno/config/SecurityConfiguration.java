package com.derso.arquitetura.pagamentoexterno.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import com.derso.arquitetura.webbase.security.RotaPublica;

// Pagar não autentica: a URL é a credencial — docs/purchase-flow-design.md#premissas-do-gateway-simulado-pagamento-externo
@Configuration
public class SecurityConfiguration {

    @Bean
    public List<RotaPublica> rotasPublicas() {
        return List.of(new RotaPublica(HttpMethod.POST, "/pagar/*"));
    }

}

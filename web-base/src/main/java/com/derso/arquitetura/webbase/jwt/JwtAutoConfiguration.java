package com.derso.arquitetura.webbase.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(TrustedJwtIssuersConfig.class)
public class JwtAutoConfiguration {

    @Bean
    public JwtValidatorService jwtValidatorService(TrustedJwtIssuersConfig config) {
        return new JwtValidatorService(config);
    }

    // Só o serviço que emite token (hoje, `clientes`) configura `jwt.private-key` — os demais
    // só validam, então não têm esse bean.
    @Bean
    @ConditionalOnProperty("jwt.private-key")
    public JwtIssuerService jwtIssuerService(
            @Value("${jwt.private-key}") String privateKeyBase64,
            @Value("${jwt.kid}") String kid,
            @Value("${jwt.issuer}") String issuer) {
        return new JwtIssuerService(privateKeyBase64, kid, issuer);
    }

}

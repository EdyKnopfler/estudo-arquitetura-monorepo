package com.derso.arquitetura.webbase.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.derso.arquitetura.webbase.internalclient.ClientSecretAuthFilter;
import com.derso.arquitetura.webbase.internalclient.InternalClientsConfig;
import com.derso.arquitetura.webbase.jwt.JwtAuthenticationFilter;
import com.derso.arquitetura.webbase.jwt.JwtValidatorService;

import jakarta.servlet.Filter;

// Único lugar do projeto que vê os tipos verbosos do Spring Security — cada `-web` só escolhe
// `security.auth-type` (jwt | client-secret) e, se precisar, declara suas rotas públicas via
// @Bean List<RotaPublica> "rotasPublicas" no próprio SecurityConfiguration — não em
// application.yaml, rota pública é decisão de código do endpoint, não configuração de
// ambiente. RotaPublica é um record de dois campos (method+pattern sempre andam juntos), não
// uma string pra parsear — HttpMethod é tipo comum do Spring Web, não dos internals verbosos
// do Spring Security que este arquivo existe pra esconder.
// @ConditionalOnWebApplication substitui o antigo `@Profile("web")` espalhado pelos módulos:
// nos papéis sagas/timeout (`web-application-type: none`), esta config inteira nem entra em
// jogo, sem depender de adivinhar o nome do profile "web" de cada serviço.
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableConfigurationProperties(InternalClientsConfig.class)
public class WebSecurityAutoConfiguration {

    @Bean("rotasPublicas")
    @ConditionalOnMissingBean(name = "rotasPublicas")
    public List<RotaPublica> rotasPublicasPadrao() {
        return List.of();
    }

    // "authFilter" é nome de bean explícito, não só o nome do método — Filter é um tipo comum
    // demais (o Boot já registra vários: requestContextFilter, characterEncodingFilter etc.),
    // então securityFilterChain() abaixo precisa de @Qualifier pra achar exatamente este.
    @Bean("authFilter")
    @ConditionalOnProperty(prefix = "security", name = "auth-type", havingValue = "jwt")
    public Filter jwtAuthFilter(JwtValidatorService validator) {
        return new JwtAuthenticationFilter(validator);
    }

    @Bean("authFilter")
    @ConditionalOnProperty(prefix = "security", name = "auth-type", havingValue = "client-secret")
    public Filter clientSecretAuthFilter(InternalClientsConfig config) {
        return new ClientSecretAuthFilter(config);
    }

    @Bean
    public DefaultSecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("authFilter") Filter authFilter,
            @Qualifier("rotasPublicas") List<RotaPublica> rotasPublicas) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                for (RotaPublica rota : rotasPublicas) {
                    auth.requestMatchers(rota.method(), rota.pattern()).permitAll();
                }
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

}

package com.derso.arquitetura.webbase.security;

import java.util.List;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.derso.arquitetura.webbase.internalclient.ClientSecretAuthFilter;
import com.derso.arquitetura.webbase.internalclient.InternalClientsConfig;
import com.derso.arquitetura.webbase.jwt.JwtAuthenticationFilter;
import com.derso.arquitetura.webbase.jwt.JwtValidatorService;

import jakarta.servlet.Filter;

// Único lugar do projeto que vê os tipos verbosos do Spring Security — cada `-web` só escolhe
// `security.auth-type` (jwt | client-secret) e, se precisar, declara suas rotas públicas via
// @Bean List<RotaPublica> "rotasPublicas" e as rotas com outra identidade via
// @Bean List<FiltroCustom> "customFilters" no próprio SecurityConfiguration — não em
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

    @Bean("customFilters")
    @ConditionalOnMissingBean(name = "customFilters")
    public List<FiltroCustom> customFiltersPadrao() {
        return List.of();
    }

    @Bean
    public DefaultSecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${security.auth-type}") AuthType authType,
            JwtValidatorService jwtValidator,
            InternalClientsConfig internalClients,
            @Qualifier("rotasPublicas") List<RotaPublica> rotasPublicas,
            @Qualifier("customFilters") List<FiltroCustom> customFilters) throws Exception {

        // Filtros criados aqui, não como @Bean: o Boot aplicaria um bean Filter a todas as rotas,
        // por fora desta chain — e a identidade por rota se perderia
        Function<AuthType, Filter> criar = tipo -> switch (tipo) {
            case JWT -> new JwtAuthenticationFilter(jwtValidator);
            case CLIENT_SECRET -> new ClientSecretAuthFilter(internalClients);
        };
        Filter authFilter = new FiltroPorRota(criar.apply(authType), customFilters, criar);

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
            // sem isso, requisição não autenticada vira 403 (entry point default) — os filtros
            // não rejeitam sozinhos, só deixam de autenticar (senão rota pública também cairia)
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

}

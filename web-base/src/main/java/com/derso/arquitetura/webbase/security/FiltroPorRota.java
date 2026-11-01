package com.derso.arquitetura.webbase.security;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Uma identidade por rota: a primeira FiltroCustom que bate escolhe o filtro, o resto vai pro padrão
class FiltroPorRota extends OncePerRequestFilter {

    private final Filter padrao;
    private final List<Map.Entry<RequestMatcher, Filter>> custom;

    FiltroPorRota(Filter padrao, List<FiltroCustom> rotas, Function<AuthType, Filter> criar) {
        this.padrao = padrao;
        this.custom = rotas.stream()
            .map(r -> Map.<RequestMatcher, Filter>entry(
                PathPatternRequestMatcher.withDefaults().matcher(r.pattern()),
                criar.apply(r.authType())
            ))
            .toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Filter escolhido = custom.stream()
            .filter(e -> e.getKey().matches(req))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(padrao);

        escolhido.doFilter(req, res, chain);
    }

}

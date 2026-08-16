package com.derso.arquitetura.webbase.jwt;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtValidatorService jwtValidatorService;

    public JwtAuthenticationFilter(JwtValidatorService jwtValidatorService) {
        this.jwtValidatorService = jwtValidatorService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        Optional<Claims> optClaims = jwtValidatorService.validateToken(token);

        if (optClaims.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        Claims claims = optClaims.get();
        Object id = claims.get("id");
        Object email = claims.get("email");

        // claims ausentes = token de formato inesperado; trata como token inválido (mesmo
        // caminho de optClaims.isEmpty() acima) em vez de estourar NPE
        if (id == null || email == null) {
            chain.doFilter(request, response);
            return;
        }

        UsuarioAutenticado dadosUsuario = new UsuarioAutenticado(id.toString(), email.toString());
        Authentication auth = new UsernamePasswordAuthenticationToken(dadosUsuario, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }

}
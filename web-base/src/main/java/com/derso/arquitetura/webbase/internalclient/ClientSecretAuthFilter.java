package com.derso.arquitetura.webbase.internalclient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ClientSecretAuthFilter extends OncePerRequestFilter {

    private final Map<String, String> clientIdsAndSecrets;

    public ClientSecretAuthFilter(InternalClientsConfig clientIdsAndSecretsConfig) {
        this.clientIdsAndSecrets = clientIdsAndSecretsConfig.getClients();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws IOException, ServletException {

        String id = req.getHeader("X-Client-Id");
        String secret = req.getHeader("X-Client-Secret");

        if (!(
            id != null && secret != null &&
            clientIdsAndSecrets.containsKey(id) && clientIdsAndSecrets.get(id).equals(secret)
        )) {
            res.setStatus(401);
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(id, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(req, res);
    }
}


package com.derso.arquitetura.webbase.internalclient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ClientSecretAuthFilter extends OncePerRequestFilter {

    private final Map<String, String> clientIdsAndSecrets;

    public ClientSecretAuthFilter(InternalClientsConfig clientIdsAndSecretsConfig) {
        this.clientIdsAndSecrets = clientIdsAndSecretsConfig.getClientsAsMap();
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
            clientIdsAndSecrets.containsKey(id) && secretsMatch(clientIdsAndSecrets.get(id), secret)
        )) {
            res.setStatus(401);
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(id, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(req, res);
    }

    // constant-time: String.equals() sai no primeiro byte diferente, vaza quanto do secret
    // acertou via timing
    private static boolean secretsMatch(String expected, String actual) {
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}


package com.derso.arquitetura.webbase.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class JwtAuthenticationFilterTest {

    private final JwtValidatorService validator = mock(JwtValidatorService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(validator);

    @AfterEach
    void limpaContextoDeSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void semHeaderAuthorizationSeguePraFrenteSemAutenticar() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(req, res);
    }

    @Test
    void headerSemPrefixoBearerSeguePraFrenteSemAutenticar() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Basic algumacoisa");
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(req, res);
    }

    @Test
    void tokenInvalidoOuExpiradoSeguePraFrenteSemAutenticar() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer token-invalido");
        when(validator.validateToken("token-invalido")).thenReturn(Optional.empty());
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(req, res);
    }

    @Test
    void tokenValidoComClaimIdAusenteNaoQuebraETrataComoInvalido() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.get("id")).thenReturn(null);
        when(claims.get("email")).thenReturn("a@b.com");

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer token-sem-id");
        when(validator.validateToken("token-sem-id")).thenReturn(Optional.of(claims));
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(req, res);
    }

    @Test
    void tokenValidoComClaimEmailAusenteNaoQuebraETrataComoInvalido() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.get("id")).thenReturn("123");
        when(claims.get("email")).thenReturn(null);

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer token-sem-email");
        when(validator.validateToken("token-sem-email")).thenReturn(Optional.of(claims));
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(req, res);
    }

    @Test
    void tokenValidoComClaimsCompletasAutentica() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.get("id")).thenReturn("123");
        when(claims.get("email")).thenReturn("a@b.com");

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer token-valido");
        when(validator.validateToken("token-valido")).thenReturn(Optional.of(claims));
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        UsuarioAutenticado principal = assertInstanceOf(UsuarioAutenticado.class,
            SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertEquals("123", principal.id());
        assertEquals("a@b.com", principal.email());
        verify(chain).doFilter(req, res);
        verify(res, never()).setStatus(401);
    }
}

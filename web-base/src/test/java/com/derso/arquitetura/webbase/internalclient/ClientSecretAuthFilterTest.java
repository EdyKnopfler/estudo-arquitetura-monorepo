package com.derso.arquitetura.webbase.internalclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class ClientSecretAuthFilterTest {

    private static final String CLIENT_ID = "reservas-interno";
    private static final String CLIENT_SECRET = "segredo-correto";

    private final InternalClientsConfig config = mock(InternalClientsConfig.class);
    private final ClientSecretAuthFilter filter;

    ClientSecretAuthFilterTest() {
        when(config.getClientsAsMap()).thenReturn(Map.of(CLIENT_ID, CLIENT_SECRET));
        filter = new ClientSecretAuthFilter(config);
    }

    @AfterEach
    void limpaContextoDeSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void credenciaisCorretasAutenticaEContinuaCadeia() throws Exception {
        HttpServletRequest req = requestCom(CLIENT_ID, CLIENT_SECRET);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertEquals(CLIENT_ID, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(chain, times(1)).doFilter(req, res);
        verify(res, never()).setStatus(eq(401));
    }

    @Test
    void secretErradaRejeitaCom401() throws Exception {
        HttpServletRequest req = requestCom(CLIENT_ID, "segredo-errado");
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(res).setStatus(401);
        verify(chain, never()).doFilter(req, res);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void clientIdDesconhecidoRejeitaCom401() throws Exception {
        HttpServletRequest req = requestCom("client-inexistente", CLIENT_SECRET);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(res).setStatus(401);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void headerIdAusenteRejeitaCom401() throws Exception {
        HttpServletRequest req = requestCom(null, CLIENT_SECRET);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(res).setStatus(401);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void headerSecretAusenteRejeitaCom401() throws Exception {
        HttpServletRequest req = requestCom(CLIENT_ID, null);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(res).setStatus(401);
        verify(chain, never()).doFilter(req, res);
    }

    private static HttpServletRequest requestCom(String clientId, String clientSecret) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Client-Id")).thenReturn(clientId);
        when(req.getHeader("X-Client-Secret")).thenReturn(clientSecret);
        return req;
    }
}

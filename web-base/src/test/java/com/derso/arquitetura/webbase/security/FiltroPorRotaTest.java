package com.derso.arquitetura.webbase.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;

class FiltroPorRotaTest {

    private final Filter padrao = mock(Filter.class);
    private final Filter jwt = mock(Filter.class);
    private final Filter clientSecret = mock(Filter.class);
    private final Map<AuthType, Filter> porTipo = Map.of(AuthType.JWT, jwt, AuthType.CLIENT_SECRET, clientSecret);

    private final MockHttpServletResponse res = new MockHttpServletResponse();
    private final FilterChain chain = mock(FilterChain.class);

    @Test
    void rotaCustomUsaOFiltroDelaENaoOPadrao() throws Exception {
        FiltroPorRota filtro = filtroCom(List.of(new FiltroCustom("/servico/**", AuthType.CLIENT_SECRET)));
        MockHttpServletRequest req = requisicao("/servico/sessoes/123/pagamento-efetuado");

        filtro.doFilter(req, res, chain);

        verify(clientSecret).doFilter(req, res, chain);
        verify(padrao, never()).doFilter(any(), any(), any());
    }

    @Test
    void rotaForaDaListaUsaOPadrao() throws Exception {
        FiltroPorRota filtro = filtroCom(List.of(new FiltroCustom("/servico/**", AuthType.CLIENT_SECRET)));
        MockHttpServletRequest req = requisicao("/sessoes/123/hotel");

        filtro.doFilter(req, res, chain);

        verify(padrao).doFilter(req, res, chain);
        verify(clientSecret, never()).doFilter(any(), any(), any());
    }

    @Test
    void semCustomSempreUsaOPadrao() throws Exception {
        FiltroPorRota filtro = filtroCom(List.of());
        MockHttpServletRequest req = requisicao("/servico/qualquer");

        filtro.doFilter(req, res, chain);

        verify(padrao).doFilter(req, res, chain);
    }

    @Test
    void duasRotasQueBatemValeAPrimeira() throws Exception {
        FiltroPorRota filtro = filtroCom(List.of(
            new FiltroCustom("/servico/sessoes/**", AuthType.JWT),
            new FiltroCustom("/servico/**", AuthType.CLIENT_SECRET)
        ));
        MockHttpServletRequest req = requisicao("/servico/sessoes/123");

        filtro.doFilter(req, res, chain);

        verify(jwt).doFilter(req, res, chain);
        verify(clientSecret, never()).doFilter(any(), any(), any());
        verify(padrao, never()).doFilter(any(), any(), any());
    }

    private FiltroPorRota filtroCom(List<FiltroCustom> rotas) {
        return new FiltroPorRota(padrao, rotas, porTipo::get);
    }

    private static MockHttpServletRequest requisicao(String caminho) {
        return new MockHttpServletRequest("PUT", caminho);
    }

}

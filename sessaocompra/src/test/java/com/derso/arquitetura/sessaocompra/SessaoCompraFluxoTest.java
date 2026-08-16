package com.derso.arquitetura.sessaocompra;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.derso.arquitetura.sessaocompra.app.dto.CriacaoSessaoResponse;
import com.derso.arquitetura.sessaocompra.reservasinterno.ReservasInternoHotelClient;
import com.derso.arquitetura.sessaocompra.reservasinterno.ReservasInternoVooClient;
import com.derso.arquitetura.webbase.jwt.UsuarioAutenticado;
import com.fasterxml.jackson.databind.ObjectMapper;

// Fronteira real de teste = o próprio módulo sessaocompra: segurança e persistência rodam de
// verdade; só reservas-interno é mockado, porque é a única dependência que sai do processo.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("web")
@TestPropertySource(properties = {
    ChaveJwtTeste.JWT_KID_PROPERTY,
    ChaveJwtTeste.JWT_ISSUER_PROPERTY,
    ChaveJwtTeste.JWT_PUBLIC_KEY_PROPERTY
})
@Import(TestcontainersConfig.class)
class SessaoCompraFluxoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservasInternoHotelClient hotelClient;

    @MockitoBean
    private ReservasInternoVooClient vooClient;

    @BeforeEach
    void mockaReservasInterno() {
        when(hotelClient.criar(any())).thenAnswer(invocation -> UUID.randomUUID());
        when(hotelClient.trocar(any(), any())).thenAnswer(invocation -> UUID.randomUUID());
        when(vooClient.criar(any())).thenAnswer(invocation -> UUID.randomUUID());
        when(vooClient.trocar(any(), any())).thenAnswer(invocation -> UUID.randomUUID());
    }

    @Test
    void fluxoFelizAceitaOrdemLivreEDestravaPagamentoSoQuandoCompleto() throws Exception {
        UUID cliente = UUID.randomUUID();
        UUID sessao = criarSessao(cliente);

        // ordem embaralhada de propósito: volta, hotel, ida
        putComoCliente(sessao, "/voo-volta", cliente).andExpect(status().isOk());
        putComoCliente(sessao, "/iniciando-pagamento", cliente).andExpect(status().isConflict());

        putComoCliente(sessao, "/hotel", cliente).andExpect(status().isOk());
        putComoCliente(sessao, "/iniciando-pagamento", cliente).andExpect(status().isConflict());

        putComoCliente(sessao, "/voo-ida", cliente).andExpect(status().isOk());
        putComoCliente(sessao, "/iniciando-pagamento", cliente).andExpect(status().isOk());
    }

    @Test
    void reSelecaoTrocaAPreReservaEmVezDeCriarDeNovo() throws Exception {
        UUID cliente = UUID.randomUUID();
        UUID sessao = criarSessao(cliente);

        putComoCliente(sessao, "/hotel", cliente).andExpect(status().isOk());
        putComoCliente(sessao, "/hotel", cliente).andExpect(status().isOk());

        verify(hotelClient, times(1)).criar(any());
        verify(hotelClient, times(1)).trocar(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "/hotel", "/voo-ida", "/voo-volta", "/iniciando-pagamento" })
    void endpointsPorItemRejeitamClienteQueNaoEDonoDaSessao(String sufixo) throws Exception {
        UUID dono = UUID.randomUUID();
        UUID outroCliente = UUID.randomUUID();
        UUID sessao = criarSessao(dono);

        putComoCliente(sessao, sufixo, outroCliente).andExpect(status().isForbidden());
    }

    private UUID criarSessao(UUID idCliente) throws Exception {
        String corpo = mockMvc.perform(post("/sessoes").with(comoCliente(idCliente)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        return objectMapper.readValue(corpo, CriacaoSessaoResponse.class).idSessao();
    }

    private org.springframework.test.web.servlet.ResultActions putComoCliente(UUID sessao, String sufixo, UUID idCliente) throws Exception {
        return mockMvc.perform(put("/sessoes/" + sessao + sufixo).with(comoCliente(idCliente)));
    }

    private RequestPostProcessor comoCliente(UUID idCliente) {
        UsuarioAutenticado usuario = new UsuarioAutenticado(idCliente.toString(), idCliente + "@teste.com");
        Authentication autenticacao = new UsernamePasswordAuthenticationToken(usuario, null, List.of());
        return authentication(autenticacao);
    }

}

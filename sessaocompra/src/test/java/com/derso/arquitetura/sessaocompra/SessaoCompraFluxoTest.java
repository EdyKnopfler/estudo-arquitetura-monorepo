package com.derso.arquitetura.sessaocompra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.derso.arquitetura.webbase.teste.PostgresTestcontainersConfig;
import com.derso.arquitetura.sessaocompra.app.dto.CriacaoSessaoResponse;
import com.derso.arquitetura.sessaocompra.pagamentointerno.PagamentoInternoClient;
import com.derso.arquitetura.sessaocompra.pagamentointerno.dto.PagamentoInternoResponse;
import com.derso.arquitetura.sessaocompra.reservasinterno.ReservasInternoHotelClient;
import com.derso.arquitetura.sessaocompra.reservasinterno.ReservasInternoVooClient;
import com.derso.arquitetura.webbase.jwt.UsuarioAutenticado;
import com.fasterxml.jackson.databind.ObjectMapper;

// reservas-interno e pagamento-interno mockados na fronteira HTTP — foco aqui é segurança/persistência
// de sessaocompra. Integração com os serviços reais é teste @Tag("integrado") (docs/testing-strategy.md).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "web", "test" })
@Import({ PostgresTestcontainersConfig.class, ChavesJwtDeTeste.class })
class SessaoCompraFluxoTest {

    private static final String URL_PAGAMENTO = "http://gateway/pagar/123";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<UUID> sessoesCriadas = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ReservasInternoHotelClient hotelClient;

    @MockitoBean
    private ReservasInternoVooClient vooClient;

    @MockitoBean
    private PagamentoInternoClient pagamentoInternoClient;

    @BeforeEach
    void mockaReservasInterno() {
        when(hotelClient.criar(any())).thenAnswer(invocation -> UUID.randomUUID());
        when(hotelClient.trocar(any(), any())).thenAnswer(invocation -> UUID.randomUUID());
        when(vooClient.criar(any())).thenAnswer(invocation -> UUID.randomUUID());
        when(vooClient.trocar(any(), any())).thenAnswer(invocation -> UUID.randomUUID());
        when(pagamentoInternoClient.criar(any(), any(), any(), any()))
            .thenReturn(new PagamentoInternoResponse(UUID.randomUUID(), URL_PAGAMENTO));
    }

    @AfterEach
    void apagaSessoesCriadas() {
        sessoesCriadas.forEach(id -> jdbc.update("delete from sessao_compra where id = ?", id));
    }

    @Test
    void criarSessaoGravaIniciadaDoClienteComInstanteDeInicio() throws Exception {
        UUID cliente = UUID.randomUUID();

        UUID sessao = criarSessao(cliente);

        Map<String, Object> linha = linha(sessao);
        assertEquals("INICIADA", linha.get("status"));
        assertEquals(cliente, linha.get("id_customer"));
        assertNotNull(linha.get("start_time"));
        assertNull(linha.get("id_reserva_hotel"));
        assertNull(linha.get("id_reserva_voo_ida"));
        assertNull(linha.get("id_reserva_voo_volta"));
    }

    @Test
    void cadaItemGravaAReservaDevolvidaNaColunaCerta() throws Exception {
        UUID cliente = UUID.randomUUID();
        UUID sessao = criarSessao(cliente);
        UUID hotel = UUID.randomUUID();
        UUID ida = UUID.randomUUID();
        UUID volta = UUID.randomUUID();

        when(hotelClient.criar(cliente)).thenReturn(hotel);
        putComoCliente(sessao, "/hotel", cliente).andExpect(status().isOk());
        assertEquals(hotel, linha(sessao).get("id_reserva_hotel"));
        assertNull(linha(sessao).get("id_reserva_voo_ida"));
        assertNull(linha(sessao).get("id_reserva_voo_volta"));

        when(vooClient.criar(cliente)).thenReturn(ida);
        putComoCliente(sessao, "/voo-ida", cliente).andExpect(status().isOk());
        assertEquals(ida, linha(sessao).get("id_reserva_voo_ida"));
        assertNull(linha(sessao).get("id_reserva_voo_volta"));

        when(vooClient.criar(cliente)).thenReturn(volta);
        putComoCliente(sessao, "/voo-volta", cliente).andExpect(status().isOk());

        Map<String, Object> linha = linha(sessao);
        assertEquals(hotel, linha.get("id_reserva_hotel"));
        assertEquals(ida, linha.get("id_reserva_voo_ida"));
        assertEquals(volta, linha.get("id_reserva_voo_volta"));
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
        putComoCliente(sessao, "/iniciando-pagamento", cliente)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.urlPagamento").value(URL_PAGAMENTO));

        assertEquals("EFETUANDO_PAGAMENTO", linha(sessao).get("status"));
    }

    @Test
    void iniciarPagamentoMandaOsIdsGravadosProPagamentoInterno() throws Exception {
        UUID cliente = UUID.randomUUID();
        UUID sessao = criarSessaoCompleta(cliente);
        Map<String, Object> antes = linha(sessao);

        putComoCliente(sessao, "/iniciando-pagamento", cliente).andExpect(status().isOk());

        verify(pagamentoInternoClient).criar(
            sessao,
            (UUID) antes.get("id_reserva_hotel"),
            (UUID) antes.get("id_reserva_voo_ida"),
            (UUID) antes.get("id_reserva_voo_volta"));
    }

    @Test
    void falhaAoCriarPagamentoMantemCriandoERetentativaRepeteOPut() throws Exception {
        UUID cliente = UUID.randomUUID();
        UUID sessao = criarSessaoCompleta(cliente);
        when(pagamentoInternoClient.criar(any(), any(), any(), any()))
            .thenThrow(new RuntimeException("pagamento-interno fora"))
            .thenReturn(new PagamentoInternoResponse(UUID.randomUUID(), URL_PAGAMENTO));

        putComoCliente(sessao, "/iniciando-pagamento", cliente).andExpect(status().isConflict());
        assertEquals("CRIANDO_PAGAMENTO", linha(sessao).get("status"));

        putComoCliente(sessao, "/iniciando-pagamento", cliente)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.urlPagamento").value(URL_PAGAMENTO));

        verify(pagamentoInternoClient, times(2)).criar(any(), any(), any(), any());
        assertEquals("EFETUANDO_PAGAMENTO", linha(sessao).get("status"));
    }

    @Test
    void alteracaoComCriacaoDoPagamentoPendenteRecusaSemTocarReservas() throws Exception {
        UUID cliente = UUID.randomUUID();
        UUID sessao = criarSessaoCompleta(cliente);
        when(pagamentoInternoClient.criar(any(), any(), any(), any()))
            .thenThrow(new RuntimeException("pagamento-interno fora"));
        putComoCliente(sessao, "/iniciando-pagamento", cliente).andExpect(status().isConflict());
        Object hotelAntes = linha(sessao).get("id_reserva_hotel");

        putComoCliente(sessao, "/hotel", cliente).andExpect(status().isConflict());

        verify(hotelClient, never()).trocar(any(), any());
        assertEquals(hotelAntes, linha(sessao).get("id_reserva_hotel"));
    }

    @Test
    void iniciarPagamentoDeNovoRepeteOPutEDevolveAMesmaUrl() throws Exception {
        UUID cliente = UUID.randomUUID();
        UUID sessao = criarSessaoCompleta(cliente);

        putComoCliente(sessao, "/iniciando-pagamento", cliente).andExpect(status().isOk());
        putComoCliente(sessao, "/iniciando-pagamento", cliente)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.urlPagamento").value(URL_PAGAMENTO));

        verify(pagamentoInternoClient, times(2)).criar(any(), any(), any(), any());
        assertEquals("EFETUANDO_PAGAMENTO", linha(sessao).get("status"));
    }

    @Test
    void iniciarPagamentoEmSessaoFinalizadaRecusaSemChamarPagamentoInterno() throws Exception {
        UUID cliente = UUID.randomUUID();
        UUID sessao = criarSessaoCompleta(cliente);
        jdbc.update("update sessao_compra set status = 'CANCELADA' where id = ?", sessao);

        putComoCliente(sessao, "/iniciando-pagamento", cliente).andExpect(status().isConflict());

        verify(pagamentoInternoClient, never()).criar(any(), any(), any(), any());
    }

    @Test
    void alteracaoDepoisDoPagamentoIniciadoRecusaSemTocarReservas() throws Exception {
        UUID cliente = UUID.randomUUID();
        UUID sessao = criarSessaoCompleta(cliente);
        putComoCliente(sessao, "/iniciando-pagamento", cliente).andExpect(status().isOk());
        Object hotelAntes = linha(sessao).get("id_reserva_hotel");

        putComoCliente(sessao, "/hotel", cliente).andExpect(status().isConflict());

        verify(hotelClient, never()).trocar(any(), any());
        assertEquals(hotelAntes, linha(sessao).get("id_reserva_hotel"));
    }

    @Test
    void reSelecaoTrocaAPreReservaAntigaEmVezDeCriarDeNovo() throws Exception {
        UUID cliente = UUID.randomUUID();
        UUID sessao = criarSessao(cliente);
        UUID primeira = UUID.randomUUID();
        UUID segunda = UUID.randomUUID();
        when(hotelClient.criar(cliente)).thenReturn(primeira);
        when(hotelClient.trocar(primeira, cliente)).thenReturn(segunda);

        putComoCliente(sessao, "/hotel", cliente).andExpect(status().isOk());
        putComoCliente(sessao, "/hotel", cliente).andExpect(status().isOk());

        verify(hotelClient, times(1)).criar(any());
        verify(hotelClient, times(1)).trocar(eq(primeira), eq(cliente));
        assertEquals(segunda, linha(sessao).get("id_reserva_hotel"));
    }

    @Test
    void falhaNaReservaNaoGravaNadaNaSessao() throws Exception {
        UUID cliente = UUID.randomUUID();
        UUID sessao = criarSessao(cliente);
        when(hotelClient.criar(any())).thenThrow(new RuntimeException("reservas-interno fora"));

        putComoCliente(sessao, "/hotel", cliente).andExpect(status().is5xxServerError());

        assertNull(linha(sessao).get("id_reserva_hotel"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/hotel", "/voo-ida", "/voo-volta", "/iniciando-pagamento" })
    void endpointsPorItemRejeitamClienteQueNaoEDonoDaSessao(String sufixo) throws Exception {
        UUID dono = UUID.randomUUID();
        UUID outroCliente = UUID.randomUUID();
        UUID sessao = criarSessao(dono);

        putComoCliente(sessao, sufixo, outroCliente).andExpect(status().isForbidden());
    }

    @Test
    void sessaoInexistenteRetorna403SemRevelarQueNaoExiste() throws Exception {
        putComoCliente(UUID.randomUUID(), "/hotel", UUID.randomUUID()).andExpect(status().isForbidden());

        verify(hotelClient, never()).criar(any());
    }

    @Test
    void semAutenticacaoRetorna401() throws Exception {
        mockMvc.perform(post("/sessoes")).andExpect(status().isUnauthorized());
    }

    private UUID criarSessao(UUID idCliente) throws Exception {
        String corpo = mockMvc.perform(post("/sessoes").with(comoCliente(idCliente)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        UUID id = objectMapper.readValue(corpo, CriacaoSessaoResponse.class).idSessao();
        sessoesCriadas.add(id);
        return id;
    }

    private UUID criarSessaoCompleta(UUID idCliente) throws Exception {
        UUID sessao = criarSessao(idCliente);
        putComoCliente(sessao, "/hotel", idCliente).andExpect(status().isOk());
        putComoCliente(sessao, "/voo-ida", idCliente).andExpect(status().isOk());
        putComoCliente(sessao, "/voo-volta", idCliente).andExpect(status().isOk());
        return sessao;
    }

    private Map<String, Object> linha(UUID sessao) {
        return jdbc.queryForMap("select * from sessao_compra where id = ?", sessao);
    }

    private ResultActions putComoCliente(UUID sessao, String sufixo, UUID idCliente) throws Exception {
        return mockMvc.perform(put("/sessoes/" + sessao + sufixo).with(comoCliente(idCliente)));
    }

    private RequestPostProcessor comoCliente(UUID idCliente) {
        UsuarioAutenticado usuario = new UsuarioAutenticado(idCliente.toString(), idCliente + "@teste.com");
        Authentication autenticacao = new UsernamePasswordAuthenticationToken(usuario, null, List.of());
        return authentication(autenticacao);
    }

}

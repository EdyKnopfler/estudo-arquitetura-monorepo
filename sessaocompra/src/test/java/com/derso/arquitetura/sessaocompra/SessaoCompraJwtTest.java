package com.derso.arquitetura.sessaocompra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.derso.arquitetura.sessaocompra.app.dto.CriacaoSessaoResponse;
import com.derso.arquitetura.sessaocompra.pagamentointerno.PagamentoInternoClient;
import com.derso.arquitetura.sessaocompra.reservasinterno.ReservasInternoHotelClient;
import com.derso.arquitetura.sessaocompra.reservasinterno.ReservasInternoVooClient;
import com.derso.arquitetura.webbase.teste.PostgresTestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;

// Token real assinado (como o emitido por `clientes`) passando pelo JwtAuthenticationFilter —
// SessaoCompraFluxoTest injeta a autenticação direto e não exercita essa parte.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "web", "test" })
@Import({ PostgresTestcontainersConfig.class, ChavesJwtDeTeste.class })
class SessaoCompraJwtTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<UUID> sessoesCriadas = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    // mesmos mocks de SessaoCompraFluxoTest: definição igual = contexto em cache compartilhado
    @MockitoBean
    private ReservasInternoHotelClient hotelClient;

    @MockitoBean
    private ReservasInternoVooClient vooClient;

    @MockitoBean
    private PagamentoInternoClient pagamentoInternoClient;

    @AfterEach
    void apagaSessoesCriadas() {
        sessoesCriadas.forEach(id -> jdbc.update("delete from sessao_compra where id = ?", id));
    }

    @Test
    void tokenValidoCriaSessaoDoClienteDoClaimId() throws Exception {
        UUID cliente = UUID.randomUUID();

        UUID sessao = criarSessao(tokenDe(cliente));

        UUID dono = jdbc.queryForObject("select id_customer from sessao_compra where id = ?", UUID.class, sessao);
        assertEquals(cliente, dono);
    }

    @Test
    void tokenDeOutroClienteNaoMexeNaSessaoAlheia() throws Exception {
        UUID sessao = criarSessao(tokenDe(UUID.randomUUID()));

        mockMvc.perform(put("/sessoes/" + sessao + "/hotel").header("Authorization", "Bearer " + tokenDe(UUID.randomUUID())))
            .andExpect(status().isForbidden());
    }

    @Test
    void tokenAssinadoPorOutraChaveComMesmoKidRetorna401() throws Exception {
        String forjado = ChavesJwtDeTeste.emissor(ChavesJwtDeTeste.gerar().getPrivate(), ChavesJwtDeTeste.KID, ChavesJwtDeTeste.EMISSOR)
            .generateToken(UUID.randomUUID().toString(), "forjado@teste.com", "CLIENTE");

        criarSessaoEsperando401(forjado);
    }

    @Test
    void tokenComIssuerDiferenteDoEsperadoParaOKidRetorna401() throws Exception {
        String outroIssuer = ChavesJwtDeTeste.emissor(ChavesJwtDeTeste.chavePrivadaConfiavel(), ChavesJwtDeTeste.KID, "outro")
            .generateToken(UUID.randomUUID().toString(), "outro@teste.com", "CLIENTE");

        criarSessaoEsperando401(outroIssuer);
    }

    @Test
    void tokenExpiradoRetorna401() throws Exception {
        Instant umaHoraAtras = Instant.now().minusSeconds(3600);
        String expirado = Jwts.builder()
            .header().keyId(ChavesJwtDeTeste.KID).and()
            .claim("id", UUID.randomUUID().toString())
            .claim("email", "expirado@teste.com")
            .issuer(ChavesJwtDeTeste.EMISSOR)
            .issuedAt(Date.from(umaHoraAtras))
            .expiration(Date.from(umaHoraAtras.plusSeconds(600)))
            .signWith(ChavesJwtDeTeste.chavePrivadaConfiavel(), Jwts.SIG.RS256)
            .compact();

        criarSessaoEsperando401(expirado);
    }

    private String tokenDe(UUID cliente) {
        return ChavesJwtDeTeste.emissorConfiavel().generateToken(cliente.toString(), cliente + "@teste.com", "CLIENTE");
    }

    private UUID criarSessao(String token) throws Exception {
        String corpo = mockMvc.perform(post("/sessoes").header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        UUID id = objectMapper.readValue(corpo, CriacaoSessaoResponse.class).idSessao();
        sessoesCriadas.add(id);
        return id;
    }

    private void criarSessaoEsperando401(String token) throws Exception {
        mockMvc.perform(post("/sessoes").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

}

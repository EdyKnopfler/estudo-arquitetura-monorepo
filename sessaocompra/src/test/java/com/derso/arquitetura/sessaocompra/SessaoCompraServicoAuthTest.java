package com.derso.arquitetura.sessaocompra;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.derso.arquitetura.sessaocompra.pagamentointerno.PagamentoInternoClient;
import com.derso.arquitetura.sessaocompra.reservasinterno.ReservasInternoHotelClient;
import com.derso.arquitetura.sessaocompra.reservasinterno.ReservasInternoVooClient;
import com.derso.arquitetura.webbase.teste.CredenciaisDev;
import com.derso.arquitetura.webbase.teste.PostgresTestcontainersConfig;

// Uma identidade por rota: /servico/** só com client-id/secret, o resto só com JWT (customFilters).
// Só a autenticação — o efeito de pagamento-efetuado (id inventado, update condicional sem linha) não importa aqui.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "web", "test" })
@Import({ PostgresTestcontainersConfig.class, ChavesJwtDeTeste.class })
class SessaoCompraServicoAuthTest {

    private static final String CLIENT_ID = CredenciaisDev.de("PAGAMENTO_INTERNO_ID");
    private static final String CLIENT_SECRET = CredenciaisDev.de("PAGAMENTO_INTERNO_SECRET");

    @Autowired
    private MockMvc mockMvc;

    // mesmos mocks de SessaoCompraFluxoTest: definição igual = contexto em cache compartilhado
    @MockitoBean
    private ReservasInternoHotelClient hotelClient;

    @MockitoBean
    private ReservasInternoVooClient vooClient;

    @MockitoBean
    private PagamentoInternoClient pagamentoInternoClient;

    @Test
    void rotaDeServicoComClientSecretPassa() throws Exception {
        mockMvc.perform(put(rotaDeServico())
                .header("X-Client-Id", CLIENT_ID)
                .header("X-Client-Secret", CLIENT_SECRET))
            .andExpect(status().isOk());
    }

    @Test
    void rotaDeServicoComJwtRetorna401() throws Exception {
        mockMvc.perform(put(rotaDeServico()).header("Authorization", "Bearer " + tokenValido()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rotaDeServicoSemCredencialRetorna401() throws Exception {
        mockMvc.perform(put(rotaDeServico()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rotaDoFrontComClientSecretRetorna401() throws Exception {
        mockMvc.perform(post("/sessoes")
                .header("X-Client-Id", CLIENT_ID)
                .header("X-Client-Secret", CLIENT_SECRET))
            .andExpect(status().isUnauthorized());
    }

    private static String rotaDeServico() {
        return "/servico/sessoes/" + UUID.randomUUID() + "/pagamento-efetuado";
    }

    private static String tokenValido() {
        UUID cliente = UUID.randomUUID();
        return ChavesJwtDeTeste.emissorConfiavel().generateToken(cliente.toString(), cliente + "@teste.com", "CLIENTE");
    }

}

package com.derso.arquitetura.clientes.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.derso.arquitetura.webbase.teste.PostgresTestcontainersConfig;
import com.derso.arquitetura.clientes.ChavesJwtDeTeste;
import com.derso.arquitetura.clientes.auth.LoginResponse;
import com.derso.arquitetura.webbase.jwt.JwtValidatorService;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfig.class)
class ClientesTest {

    private static final String SENHA = "senha-de-teste";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> emailsUsados = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtValidatorService validator;

    @DynamicPropertySource
    static void chavesJwt(DynamicPropertyRegistry registry) {
        ChavesJwtDeTeste.registrar(registry);
    }

    @AfterEach
    void apagaClientesCriados() {
        emailsUsados.forEach(email -> jdbc.update("delete from clientes where email = ?", email));
    }

    @Test
    void cadastroGravaClienteComSenhaEmHashSemDevolverASenha() throws Exception {
        String email = novoEmail();
        String cpf = novoCpf();

        cadastrar("Fulano", email, cpf)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.senha").isEmpty());

        Map<String, Object> linha = jdbc.queryForMap("select * from clientes where email = ?", email);
        assertEquals("Fulano", linha.get("nome"));
        assertEquals(cpf, linha.get("cpf"));
        String senhaGravada = (String) linha.get("senha");
        assertNotEquals(SENHA, senhaGravada);
        assertTrue(passwordEncoder.matches(SENHA, senhaGravada));
    }

    @Test
    void emailDuplicadoRetorna409SemSegundaLinha() throws Exception {
        String email = novoEmail();
        cadastrar("Fulano", email, novoCpf()).andExpect(status().isCreated());

        cadastrar("Beltrano", email, novoCpf()).andExpect(status().isConflict());

        assertEquals(1, contar("select count(*) from clientes where email = ?", email));
    }

    @Test
    void cpfDuplicadoRetorna409SemSegundaLinha() throws Exception {
        String cpf = novoCpf();
        cadastrar("Fulano", novoEmail(), cpf).andExpect(status().isCreated());

        cadastrar("Beltrano", novoEmail(), cpf).andExpect(status().isConflict());

        assertEquals(1, contar("select count(*) from clientes where cpf = ?", cpf));
    }

    @Test
    void cadastroInvalidoRetorna400SemGravar() throws Exception {
        String email = novoEmail();

        cadastrar("", email, novoCpf()).andExpect(status().isBadRequest());
        cadastrar("Fulano", email, "123456789012").andExpect(status().isBadRequest());

        assertEquals(0, contar("select count(*) from clientes where email = ?", email));
    }

    @Test
    void loginCorretoEmiteTokenComClaimsDoClienteGravado() throws Exception {
        String email = novoEmail();
        cadastrar("Fulano", email, novoCpf()).andExpect(status().isCreated());

        String token = login(email, SENHA);

        Claims claims = validator.validateToken(token).orElseThrow();
        UUID idGravado = jdbc.queryForObject("select id from clientes where email = ?", UUID.class, email);
        assertEquals(idGravado.toString(), claims.get("id"));
        assertEquals(email, claims.get("email"));
    }

    @Test
    void loginComSenhaErradaRetorna401() throws Exception {
        String email = novoEmail();
        cadastrar("Fulano", email, novoCpf()).andExpect(status().isCreated());

        tentarLogin(email, "senha-errada")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void loginComEmailInexistenteRetorna401() throws Exception {
        tentarLogin(novoEmail(), SENHA).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDoLoginAutorizaCancelarAPropriaInscricao() throws Exception {
        String email = novoEmail();
        cadastrar("Fulano", email, novoCpf()).andExpect(status().isCreated());
        UUID id = jdbc.queryForObject("select id from clientes where email = ?", UUID.class, email);
        String token = login(email, SENHA);

        mockMvc.perform(delete("/clientes/" + id).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        assertEquals(0, contar("select count(*) from clientes where id = ?", id));
    }

    @Test
    void cancelarInscricaoDeOutroClienteRetorna403EMantemALinha() throws Exception {
        String emailAlvo = novoEmail();
        cadastrar("Alvo", emailAlvo, novoCpf()).andExpect(status().isCreated());
        UUID idAlvo = jdbc.queryForObject("select id from clientes where email = ?", UUID.class, emailAlvo);

        String emailAtacante = novoEmail();
        cadastrar("Atacante", emailAtacante, novoCpf()).andExpect(status().isCreated());
        String tokenAtacante = login(emailAtacante, SENHA);

        mockMvc.perform(delete("/clientes/" + idAlvo).header("Authorization", "Bearer " + tokenAtacante))
            .andExpect(status().isForbidden());

        assertEquals(1, contar("select count(*) from clientes where id = ?", idAlvo));
    }

    @Test
    void cancelarInscricaoSemTokenNaoAutoriza() throws Exception {
        String email = novoEmail();
        cadastrar("Fulano", email, novoCpf()).andExpect(status().isCreated());
        UUID id = jdbc.queryForObject("select id from clientes where email = ?", UUID.class, email);

        mockMvc.perform(delete("/clientes/" + id)).andExpect(status().isUnauthorized());

        assertEquals(1, contar("select count(*) from clientes where id = ?", id));
    }

    private ResultActions cadastrar(String nome, String email, String cpf) throws Exception {
        String corpo = objectMapper.writeValueAsString(Map.of("nome", nome, "email", email, "cpf", cpf, "senha", SENHA));
        return mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private ResultActions tentarLogin(String email, String senha) throws Exception {
        String corpo = objectMapper.writeValueAsString(Map.of("email", email, "senha", senha));
        return mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private String login(String email, String senha) throws Exception {
        String corpo = tentarLogin(email, senha)
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(corpo, LoginResponse.class).token();
    }

    private int contar(String sql, Object parametro) {
        return jdbc.queryForObject(sql, Integer.class, parametro);
    }

    private String novoEmail() {
        String email = UUID.randomUUID() + "@teste.com";
        emailsUsados.add(email);
        return email;
    }

    private static String novoCpf() {
        return String.format("%011d", ThreadLocalRandom.current().nextLong(100_000_000_000L));
    }

}

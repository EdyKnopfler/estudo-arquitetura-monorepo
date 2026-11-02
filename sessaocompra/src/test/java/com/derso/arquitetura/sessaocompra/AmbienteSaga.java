package com.derso.arquitetura.sessaocompra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import com.derso.arquitetura.sagas.Messaging;
import com.derso.arquitetura.webbase.teste.CredenciaisDev;
import com.derso.arquitetura.webbase.teste.ServicoEmContainer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

// Infra dos testes integrados da SAGA: os serviços em container (sobem e caem por fase), Postgres com um
// database por serviço, RabbitMQ, e as conferências narradas no roteiro —
// docs/testing-strategy.md#serviços-em-fases
final class AmbienteSaga implements AutoCloseable {

    record Servico(String nome, GenericContainer<?> container) {
    }

    @FunctionalInterface
    interface Leitura<T> {
        T ler() throws Exception;
    }

    private static final Duration ESPERA = Duration.ofSeconds(60);

    private static final List<String> BANCOS_EXTRAS = List.of(
        "interno_hotel", "interno_voo", "externo_hotel", "externo_voo", "interno_pagamento", "externo_pagamento"
    );

    private static final List<String> CREDENCIAIS = List.of(
        "SESSAO_COMPRA_ID", "SESSAO_COMPRA_SECRET",
        "PAGAMENTO_INTERNO_ID", "PAGAMENTO_INTERNO_SECRET",
        "PAGAMENTO_EXTERNO_ID", "PAGAMENTO_EXTERNO_SECRET",
        "RESERVAS_INTERNO_WEB_HOTEL_ID", "RESERVAS_INTERNO_WEB_HOTEL_SECRET",
        "RESERVAS_INTERNO_WEB_VOO_ID", "RESERVAS_INTERNO_WEB_VOO_SECRET",
        "RESERVAS_EXTERNO_HOTEL_ID", "RESERVAS_EXTERNO_HOTEL_SECRET",
        "RESERVAS_EXTERNO_VOO_ID", "RESERVAS_EXTERNO_VOO_SECRET"
    );

    static final ObjectMapper JSON = new ObjectMapper();

    final RoteiroTesteIntegrado roteiro;

    final Servico sessaoWeb;
    final Servico sessaoSagas;
    final Servico hotelExterno;
    final Servico hotelWeb;
    final Servico hotelSagas;
    final Servico vooExterno;
    final Servico vooWeb;
    final Servico vooIdaSagas;
    final Servico vooVoltaSagas;
    final Servico pagamentoExterno;
    final Servico pagamentoWeb;
    final Servico pagamentoSagas;

    private final Network network;
    private final PostgreSQLContainer postgres;
    private final RabbitMQContainer rabbit;

    // só monta: nada sobe até subirInfra()/subir()
    AmbienteSaga(String arquivoRoteiro) {
        // sem contexto Spring o logback fica no default (DEBUG) e o roteiro some no log do Testcontainers
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);
        // avisa a cada build que o contexto (o jar) é grande — esperado aqui
        ((Logger) LoggerFactory.getLogger(ImageFromDockerfile.class)).setLevel(Level.ERROR);

        roteiro = new RoteiroTesteIntegrado(Path.of("target", arquivoRoteiro));
        network = Network.newNetwork();

        // um database por serviço, como no compose (databases.sql.example)
        postgres = new PostgreSQLContainer("postgres:18.1")
            .withDatabaseName("sessaocompra")
            .withUsername("app")
            .withPassword("app")
            .withNetwork(network)
            .withNetworkAliases("db")
            .withCopyToContainer(Transferable.of(criarBancos()), "/docker-entrypoint-initdb.d/bancos.sql");

        rabbit = new RabbitMQContainer("rabbitmq:4.2.2-management-alpine")
            .withNetwork(network)
            .withNetworkAliases("broker");

        Map<String, String> ambiente = ambiente();
        sessaoWeb = servico(ambiente, "sessaocompra (web)", "sessaocompra", "sessaocompra-web", "web", "SessaoCompraApplication");
        sessaoWeb.container().withExposedPorts(8080);
        sessaoSagas = servico(ambiente, "sessaocompra (sagas)", "sessaocompra", "sessaocompra-sagas", "sagas", "SessaoCompraApplication");
        hotelExterno = servico(ambiente, "reservas-externo (hotel)", "reservas-externo", "reservas-externo-hotel", "hotel,test", "ReservasExternoApplication");
        hotelWeb = servico(ambiente, "reservas-interno (hotel, web)", "reservas-interno", "reservas-interno-hotel-web", "hotel,web", "ReservasInternoApplication");
        hotelSagas = servico(ambiente, "reservas-interno (hotel, sagas)", "reservas-interno", "reservas-interno-hotel-sagas", "hotel,sagas", "ReservasInternoApplication");
        vooExterno = servico(ambiente, "reservas-externo (voo)", "reservas-externo", "reservas-externo-voo", "voo,test", "ReservasExternoApplication");
        vooWeb = servico(ambiente, "reservas-interno (voo, web)", "reservas-interno", "reservas-interno-voo-web", "voo,web", "ReservasInternoApplication");
        vooIdaSagas = servico(ambiente, "reservas-interno (voo-ida, sagas)", "reservas-interno", "reservas-interno-voo-ida-sagas", "voo,sagas", "ReservasInternoApplication");
        soUmaPerna(vooIdaSagas, "voo-ida", "hotel", "voo-volta", "idReservaVooIda");
        vooVoltaSagas = servico(ambiente, "reservas-interno (voo-volta, sagas)", "reservas-interno", "reservas-interno-voo-volta-sagas", "voo,sagas", "ReservasInternoApplication");
        soUmaPerna(vooVoltaSagas, "voo-volta", "voo-ida", "sessaocompra", "idReservaVooVolta");
        pagamentoExterno = servico(ambiente, "pagamento-externo (gateway)", "pagamento-externo", "pagamento-externo", "test", "PagamentoExternoApplication");
        pagamentoExterno.container().withExposedPorts(8086);
        pagamentoWeb = servico(ambiente, "pagamento-interno (web)", "pagamento-interno", "pagamento-interno-web", "web", "PagamentoInternoApplication");
        pagamentoWeb.container().withExposedPorts(8087);
        pagamentoSagas = servico(ambiente, "pagamento-interno (sagas)", "pagamento-interno", "pagamento-interno-sagas", "sagas", "PagamentoInternoApplication");
    }

    @Override
    public void close() {
        Stream.of(sessaoWeb, sessaoSagas, hotelExterno, hotelWeb, hotelSagas,
                vooExterno, vooWeb, vooIdaSagas, vooVoltaSagas, pagamentoExterno, pagamentoWeb, pagamentoSagas)
            .forEach(servico -> servico.container().stop());
        rabbit.stop();
        postgres.stop();
        network.close();
        roteiro.close();
    }

    // --- containers ---

    void subirInfra() {
        roteiro.etapa("0. Infra");
        roteiro.fazendo("subindo Postgres (um database por serviço) e RabbitMQ");
        postgres.start();
        rabbit.start();
        roteiro.ok("Postgres e RabbitMQ no ar");
    }

    void subir(Servico servico) {
        roteiro.fazendo("subindo " + servico.nome());
        servico.container().start();
        roteiro.ok(servico.nome() + " no ar");
    }

    void derrubar(Servico servico) {
        servico.container().stop();
        roteiro.ok(servico.nome() + " derrubado");
    }

    String url(Servico servico, int porta) {
        return "http://" + servico.container().getHost() + ":" + servico.container().getMappedPort(porta);
    }

    // mesmo ambiente pra todos: cada serviço lê só o que é dele
    private Map<String, String> ambiente() {
        Map<String, String> ambiente = new HashMap<>();
        ambiente.put("DB_HOST", "db");
        ambiente.put("DB_PORT", "5432");
        ambiente.put("DB_USER", postgres.getUsername());
        ambiente.put("DB_PASSWORD", postgres.getPassword());
        ambiente.put("BROKER_HOST", "broker");
        ambiente.put("BROKER_PORT", "5672");
        ambiente.put("BROKER_USER", rabbit.getAdminUsername());
        ambiente.put("BROKER_PASSWORD", rabbit.getAdminPassword());
        // portas ficam no default de cada serviço; o host é o alias na network
        ambiente.put("SESSAO_COMPRA_WEB_HOST", "sessaocompra-web");
        ambiente.put("RESERVAS_INTERNO_WEB_HOTEL_HOST", "reservas-interno-hotel-web");
        ambiente.put("RESERVAS_INTERNO_WEB_VOO_HOST", "reservas-interno-voo-web");
        ambiente.put("RESERVAS_EXTERNO_HOTEL_HOST", "reservas-externo-hotel");
        ambiente.put("RESERVAS_EXTERNO_VOO_HOST", "reservas-externo-voo");
        ambiente.put("PAGAMENTO_EXTERNO_HOST", "pagamento-externo");
        ambiente.put("PAGAMENTO_INTERNO_HOST", "pagamento-interno-web");
        ambiente.put("JWT_KID", ChavesJwtDeTeste.KID);
        ambiente.put("JWT_ISSUER", ChavesJwtDeTeste.EMISSOR);
        ambiente.put("JWT_PUBLIC_KEY", ChavesJwtDeTeste.chavePublicaConfiavel());
        // simuladores sem chaos (só valem com o profile `test`)
        ambiente.put("SIMULACAO_RESULTADO", "SUCESSO");
        ambiente.put("SIMULACAO_RESULTADOPAGAR", "ACEITO");
        CREDENCIAIS.forEach(variavel -> ambiente.put(variavel, CredenciaisDev.de(variavel)));
        return ambiente;
    }

    private Servico servico(Map<String, String> ambiente, String nome, String modulo, String alias, String perfis, String aplicacao) {
        GenericContainer<?> container = ServicoEmContainer.de(modulo)
            .withNetwork(network)
            .withNetworkAliases(alias)
            .withEnv(ambiente)
            .withEnv("SPRING_PROFILES_ACTIVE", perfis)
            .waitingFor(Wait.forLogMessage(".*Started " + aplicacao + ".*\\n", 1)
                .withStartupTimeout(Duration.ofMinutes(3)));
        return new Servico(nome, container);
    }

    // em produção as duas pernas dividem o processo; aqui uma por container, pra ver cada uma confirmar e
    // reverter. Lista indexada só é lida da fonte de maior prioridade: o env substitui a do application-voo.yaml
    private static void soUmaPerna(Servico servico, String estafila, String filaanterior, String proximafila, String campoid) {
        servico.container()
            .withEnv("SAGAS_CONSUMERS_0_ESTAFILA", estafila)
            .withEnv("SAGAS_CONSUMERS_0_FILAANTERIOR", filaanterior)
            .withEnv("SAGAS_CONSUMERS_0_PROXIMAFILA", proximafila)
            .withEnv("SAGAS_CONSUMERS_0_CAMPOID", campoid);
    }

    private static String criarBancos() {
        StringBuilder sql = new StringBuilder();
        BANCOS_EXTRAS.forEach(banco -> sql.append("create database ").append(banco).append(";\n"));
        return sql.toString();
    }

    // --- conferências ---

    void conferir(String onde, String oQue, Object esperado, Object atual) {
        assertEquals(esperado, atual, "[" + onde + "] " + oQue);
        roteiro.ok("[" + onde + "] " + oQue + " = " + atual);
    }

    void conferirReserva(String interno, String externo, UUID id, String status, boolean confirmada) {
        Map<String, Object> reserva = linha(interno, "select status, id_externo from reservas where id = ?", id);
        conferir(interno, "reserva " + id, status, reserva.get("status"));
        Object idExterno = reserva.get("id_externo");
        conferir(externo, "reserva " + idExterno + " confirmada", confirmada,
            coluna(externo, "select confirmado from reservas where id = ?", idExterno));
    }

    // lê sem consumir: a mensagem volta pra fila e o próximo nó a recebe igual
    void conferirMensagem(String fila, double tipo, UUID idSessao, Map<String, UUID> ids) throws Exception {
        conferir("RabbitMQ", "mensagens na fila " + fila, 1, mensagens(fila));
        roteiro.fazendo("lendo a mensagem da fila " + fila + " sem consumir");
        JsonNode mensagem = JSON.readTree(espiar(fila));
        roteiro.dado(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(mensagem));

        String nomeTipo = tipo == Messaging.EXECUTE ? "EXECUTE" : "DESFACA";
        assertEquals(tipo, mensagem.get("tipo").asDouble(), "tipo");
        assertEquals(idSessao.toString(), mensagem.get("rastreio").asText(), "rastreio");
        ids.forEach((campo, id) -> assertEquals(id.toString(), mensagem.get(campo).asText(), campo));
        roteiro.ok(nomeTipo + ", rastreio = sessão, ids do pagamento e das três reservas conferem");
    }

    void esperarFila(String fila, int quantidade) throws Exception {
        esperar("[RabbitMQ] fila " + fila + " com " + quantidade + (quantidade == 1 ? " mensagem" : " mensagens"),
            () -> mensagens(fila) == quantidade);
    }

    void esperar(String oQue, Leitura<Boolean> condicao) throws Exception {
        roteiro.fazendo("esperando " + oQue);
        Instant limite = Instant.now().plus(ESPERA);
        while (!condicao.ler()) {
            if (Instant.now().isAfter(limite)) {
                throw new AssertionError("não aconteceu em " + ESPERA.toSeconds() + "s: " + oQue);
            }
            Thread.sleep(500);
        }
        roteiro.ok(oQue);
    }

    void conferirDlqVazia() throws Exception {
        conferir("RabbitMQ", "mensagens na DLQ (fila " + Messaging.ERRORS_ROUTING_KEY + ")", 0, mensagens(Messaging.ERRORS_ROUTING_KEY));
    }

    // --- RabbitMQ ---

    // prontas + entregues sem ack: a fila só zera quando o consumidor terminou
    int mensagens(String fila) throws Exception {
        String saida = rabbit.execInContainer("rabbitmqctl", "list_queues", "--quiet", "--no-table-headers", "name", "messages")
            .getStdout();
        return saida.lines()
            .map(linha -> linha.trim().split("\\s+"))
            .filter(campos -> campos.length == 2 && campos[0].equals(fila))
            .mapToInt(campos -> Integer.parseInt(campos[1]))
            .findFirst()
            .orElse(0);
    }

    private String espiar(String fila) throws Exception {
        ConnectionFactory fabrica = new ConnectionFactory();
        fabrica.setHost(rabbit.getHost());
        fabrica.setPort(rabbit.getAmqpPort());
        fabrica.setUsername(rabbit.getAdminUsername());
        fabrica.setPassword(rabbit.getAdminPassword());

        try (com.rabbitmq.client.Connection conexao = fabrica.newConnection();
             Channel canal = conexao.createChannel()) {
            GetResponse resposta = canal.basicGet(fila, false);
            if (resposta == null) {
                throw new AssertionError("fila " + fila + " vazia");
            }
            canal.basicNack(resposta.getEnvelope().getDeliveryTag(), false, true);
            return new String(resposta.getBody(), StandardCharsets.UTF_8);
        }
    }

    // --- Postgres ---

    // pra semear o cenário antes de o serviço dono do banco subir; ele valida o mesmo histórico depois
    void migrar(String banco, String modulo) {
        Flyway.configure()
            .dataSource(jdbcUrl(banco), postgres.getUsername(), postgres.getPassword())
            .locations("filesystem:../" + modulo + "/src/main/resources/db/migration")
            .load()
            .migrate();
    }

    void executar(String banco, String sql, Object... parametros) {
        try (Connection conexao = conectar(banco);
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                comando.setObject(i + 1, parametros[i]);
            }
            comando.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    Object coluna(String banco, String sql, Object... parametros) {
        return linha(banco, sql, parametros).values().iterator().next();
    }

    Map<String, Object> linha(String banco, String sql, Object... parametros) {
        try (Connection conexao = conectar(banco);
             PreparedStatement consulta = conexao.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                consulta.setObject(i + 1, parametros[i]);
            }
            try (ResultSet resultado = consulta.executeQuery()) {
                if (!resultado.next()) {
                    throw new AssertionError("[" + banco + "] nenhuma linha: " + sql);
                }
                Map<String, Object> colunas = new HashMap<>();
                for (int i = 1; i <= resultado.getMetaData().getColumnCount(); i++) {
                    colunas.put(resultado.getMetaData().getColumnName(i), resultado.getObject(i));
                }
                return colunas;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private Connection conectar(String banco) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(banco), postgres.getUsername(), postgres.getPassword());
    }

    private String jdbcUrl(String banco) {
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + banco;
    }

}

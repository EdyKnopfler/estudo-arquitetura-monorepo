package com.derso.arquitetura.sessaocompra;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.derso.arquitetura.sagas.Messaging;
import com.derso.arquitetura.webbase.teste.CredenciaisDev;

// Reversão pela cadeia inteira: o voo-volta, última reserva, falha por negócio (pré-reserva vencida no
// externo) e o DESFACA volta por voo-ida → hotel → pagamento (estorno) → sessaocompra (volta ao pedido).
// Parte do webhook: sessão e pré-reservas semeadas direto nos bancos. Mesmo esquema de fases do
// SagaCaminhoFelizSistemaTest. Narrativa: target/roteiro-falha-voo-volta.txt. Sistema: só roda com -Psistema (docs/testing-strategy.md).
@Tag("sistema")
class SagaFalhaVooVoltaSistemaTest {

    private static final String AGORA = "(now() at time zone 'UTC')";

    private static AmbienteSaga ambiente;
    private static RoteiroTesteIntegrado roteiro;

    private final UUID idSessao = UUID.randomUUID();
    private final UUID idHotel = UUID.randomUUID();
    private final UUID idVooIda = UUID.randomUUID();
    private final UUID idVooVolta = UUID.randomUUID();
    private UUID idPagamento;
    private UUID idTransacao;
    private UUID chaveDaTentativaPaga;

    @BeforeAll
    static void montar() {
        ambiente = new AmbienteSaga("roteiro-falha-voo-volta.txt");
        roteiro = ambiente.roteiro;
    }

    // nulo se a montagem falhou (ex.: .env não carregado)
    @AfterAll
    static void desmontar() {
        if (ambiente != null) {
            ambiente.close();
        }
    }

    @Test
    void voltaVencidaRevertePelaCadeiaEstornaEVoltaAoPedido() throws Exception {
        try {
            ambiente.subirInfra();
            semearCenario();
            pagar();
            noPagamento();
            noHotel();
            noVooIda();
            noVooVolta();
            reverterVooIda();
            reverterHotel();
            estornarPagamento();
            voltarAoPedido();
            roteiro.etapa("Fim: estornado, reservas de volta à pré-reserva, sessão no pedido, nenhuma mensagem na DLQ");
        } catch (Throwable erro) {
            roteiro.falha(erro);
            throw erro;
        }
    }

    private void semearCenario() {
        roteiro.etapa("1. Cenário semeado: sessão aguardando pagamento, três pré-reservas, volta vencida no externo");
        roteiro.fazendo("criando os schemas com as migrations de cada serviço");
        ambiente.migrar("sessaocompra", "sessaocompra");
        ambiente.migrar("interno_hotel", "reservas-interno");
        ambiente.migrar("interno_voo", "reservas-interno");
        ambiente.migrar("externo_hotel", "reservas-externo");
        ambiente.migrar("externo_voo", "reservas-externo");
        ambiente.migrar("interno_pagamento", "pagamento-interno");
        ambiente.migrar("externo_pagamento", "pagamento-externo");
        roteiro.ok("schemas criados");

        UUID idCliente = UUID.randomUUID();
        semearReserva("interno_hotel", "externo_hotel", idHotel, idCliente, AGORA, "hotel");
        semearReserva("interno_voo", "externo_voo", idVooIda, idCliente, AGORA, "ida");
        // validade da pré-reserva no externo é 1h: confirmar a volta dá 404, falha de negócio
        semearReserva("interno_voo", "externo_voo", idVooVolta, idCliente, AGORA + " - interval '2 hours'", "volta (criada há 2h)");

        ambiente.executar("sessaocompra",
            "insert into sessao_compra (id, status, id_customer, id_reserva_hotel, id_reserva_voo_ida, id_reserva_voo_volta, start_time, prazo_iniciada)"
                + " values (?, 'EFETUANDO_PAGAMENTO', ?, ?, ?, ?, " + AGORA + ", " + AGORA + " + interval '15 minutes')",
            idSessao, idCliente, idHotel, idVooIda, idVooVolta);
        roteiro.ok("[sessaocompra] sessão " + idSessao + " semeada em EFETUANDO_PAGAMENTO");
    }

    private void semearReserva(String interno, String externo, UUID id, UUID idCliente, String criacao, String qual) {
        UUID idExterno = UUID.randomUUID();
        ambiente.executar(externo,
            "insert into reservas (id, id_cliente, criacao, confirmado) values (?, ?, " + criacao + ", false)", idExterno, idCliente);
        ambiente.executar(interno, "insert into reservas (id, id_externo, status) values (?, ?, 'PRE_RESERVADA')", id, idExterno);
        roteiro.ok("[" + interno + "] pré-reserva " + qual + " " + id + " → [" + externo + "] " + idExterno);
    }

    private void pagar() throws Exception {
        roteiro.etapa("2. Pagamento, até o webhook publicar o início da SAGA");
        ambiente.subir(ambiente.sessaoWeb);
        ambiente.subir(ambiente.pagamentoExterno);
        ambiente.subir(ambiente.pagamentoWeb);

        // o que o sessaocompra faria no avançar pro pagamento
        roteiro.fazendo("PUT /pagamentos/" + idSessao + " no pagamento-interno, como sessaocompra");
        RestClient.builder()
            .baseUrl(ambiente.url(ambiente.pagamentoWeb, 8087))
            .defaultHeader("X-Client-Id", CredenciaisDev.de("SESSAO_COMPRA_ID"))
            .defaultHeader("X-Client-Secret", CredenciaisDev.de("SESSAO_COMPRA_SECRET"))
            .build()
            .put().uri("/pagamentos/{id}", idSessao)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("idReservaHotel", idHotel, "idReservaVooIda", idVooIda, "idReservaVooVolta", idVooVolta))
            .retrieve()
            .toBodilessEntity();

        Map<String, Object> pagamento = ambiente.linha("interno_pagamento",
            "select id, status, id_externo, chave_idempotencia from pagamentos where id_sessao_compra = ?", idSessao);
        idPagamento = (UUID) pagamento.get("id");
        idTransacao = (UUID) pagamento.get("id_externo");
        chaveDaTentativaPaga = (UUID) pagamento.get("chave_idempotencia");
        ambiente.conferir("interno_pagamento", "pagamento " + idPagamento, "AGUARDANDO_PAGAMENTO", pagamento.get("status"));
        ambiente.conferir("externo_pagamento", "transação " + idTransacao, "PENDENTE", statusDaTransacao());

        roteiro.fazendo("POST /pagar/" + idTransacao + " no gateway");
        RestClient.create(ambiente.url(ambiente.pagamentoExterno, 8086)).post().uri("/pagar/{id}", idTransacao).retrieve().toBodilessEntity();
        ambiente.conferir("externo_pagamento", "transação " + idTransacao, "PAGO", statusDaTransacao());
        ambiente.conferir("interno_pagamento", "pagamento " + idPagamento, "PAGO", statusDoPagamento());
        ambiente.conferir("sessaocompra", "status da sessão", "PAGAMENTO_EFETUADO", statusDaSessao());

        ambiente.derrubar(ambiente.pagamentoWeb);
        ambiente.derrubar(ambiente.pagamentoExterno);
        ambiente.derrubar(ambiente.sessaoWeb);
        conferirMensagem("pagamento", Messaging.EXECUTE);
    }

    private void noPagamento() throws Exception {
        roteiro.etapa("3. Nó pagamento");
        ambiente.subir(ambiente.pagamentoSagas);
        ambiente.esperarFila("pagamento", 0);
        ambiente.esperarFila("hotel", 1);
        ambiente.derrubar(ambiente.pagamentoSagas);
        conferirMensagem("hotel", Messaging.EXECUTE);
    }

    private void noHotel() throws Exception {
        roteiro.etapa("4. Nó hotel: confirma");
        ambiente.subir(ambiente.hotelExterno);
        ambiente.subir(ambiente.hotelSagas);
        ambiente.esperarFila("hotel", 0);
        ambiente.esperarFila("voo-ida", 1);
        ambiente.conferirReserva("interno_hotel", "externo_hotel", idHotel, "RESERVADA", true);
        ambiente.derrubar(ambiente.hotelSagas);
        ambiente.derrubar(ambiente.hotelExterno);
        conferirMensagem("voo-ida", Messaging.EXECUTE);
    }

    private void noVooIda() throws Exception {
        roteiro.etapa("5. Nó voo-ida: confirma");
        ambiente.subir(ambiente.vooExterno);
        ambiente.subir(ambiente.vooIdaSagas);
        ambiente.esperarFila("voo-ida", 0);
        ambiente.esperarFila("voo-volta", 1);
        ambiente.conferirReserva("interno_voo", "externo_voo", idVooIda, "RESERVADA", true);
        ambiente.derrubar(ambiente.vooIdaSagas);
        ambiente.derrubar(ambiente.vooExterno);
        conferirMensagem("voo-volta", Messaging.EXECUTE);
    }

    private void noVooVolta() throws Exception {
        roteiro.etapa("6. Nó voo-volta: falha por negócio (pré-reserva vencida) — a reversão começa");
        ambiente.subir(ambiente.vooExterno);
        ambiente.subir(ambiente.vooVoltaSagas);
        ambiente.esperarFila("voo-volta", 0);
        ambiente.esperarFila("voo-ida", 1);
        ambiente.conferir("RabbitMQ", "mensagens na fila sessaocompra (EXECUTE não seguiu)", 0, ambiente.mensagens("sessaocompra"));
        ambiente.conferirReserva("interno_voo", "externo_voo", idVooVolta, "PRE_RESERVADA", false);
        ambiente.derrubar(ambiente.vooVoltaSagas);
        ambiente.derrubar(ambiente.vooExterno);
        conferirMensagem("voo-ida", Messaging.DESFACA);
    }

    private void reverterVooIda() throws Exception {
        roteiro.etapa("7. Nó voo-ida: desconfirma");
        ambiente.subir(ambiente.vooExterno);
        ambiente.subir(ambiente.vooIdaSagas);
        ambiente.esperarFila("voo-ida", 0);
        ambiente.esperarFila("hotel", 1);
        ambiente.conferirReserva("interno_voo", "externo_voo", idVooIda, "PRE_RESERVADA", false);
        ambiente.derrubar(ambiente.vooIdaSagas);
        ambiente.derrubar(ambiente.vooExterno);
        conferirMensagem("hotel", Messaging.DESFACA);
    }

    private void reverterHotel() throws Exception {
        roteiro.etapa("8. Nó hotel: desconfirma");
        ambiente.subir(ambiente.hotelExterno);
        ambiente.subir(ambiente.hotelSagas);
        ambiente.esperarFila("hotel", 0);
        ambiente.esperarFila("pagamento", 1);
        ambiente.conferirReserva("interno_hotel", "externo_hotel", idHotel, "PRE_RESERVADA", false);
        ambiente.derrubar(ambiente.hotelSagas);
        ambiente.derrubar(ambiente.hotelExterno);
        conferirMensagem("pagamento", Messaging.DESFACA);
    }

    private void estornarPagamento() throws Exception {
        roteiro.etapa("9. Nó pagamento: estorna e reabre a linha pra tentativa nova");
        ambiente.subir(ambiente.pagamentoExterno);
        ambiente.subir(ambiente.pagamentoSagas);
        ambiente.esperarFila("pagamento", 0);
        ambiente.esperarFila("sessaocompra", 1);
        ambiente.conferir("externo_pagamento", "transação " + idTransacao, "ESTORNADO", statusDaTransacao());

        Map<String, Object> reaberto = ambiente.linha("interno_pagamento",
            "select status, id_externo, chave_idempotencia <> ? as chave_nova from pagamentos where id = ?",
            chaveDaTentativaPaga, idPagamento);
        ambiente.conferir("interno_pagamento", "pagamento " + idPagamento, "CRIANDO", reaberto.get("status"));
        ambiente.conferir("interno_pagamento", "transação vigente", null, reaberto.get("id_externo"));
        ambiente.conferir("interno_pagamento", "chave de idempotência nova", true, reaberto.get("chave_nova"));
        ambiente.derrubar(ambiente.pagamentoSagas);
        ambiente.derrubar(ambiente.pagamentoExterno);
        conferirMensagem("sessaocompra", Messaging.DESFACA);
    }

    private void voltarAoPedido() throws Exception {
        roteiro.etapa("10. Nó sessaocompra: volta ao pedido (fim da reversão)");
        ambiente.subir(ambiente.sessaoSagas);
        ambiente.esperarFila("sessaocompra", 0);
        ambiente.conferir("sessaocompra", "status da sessão", "INICIADA", statusDaSessao());
        ambiente.conferir("sessaocompra", "prazo de INICIADA reiniciado (no futuro)", true,
            ambiente.coluna("sessaocompra", "select prazo_iniciada > " + AGORA + " from sessao_compra where id = ?", idSessao));
        ambiente.conferirDlqVazia();
        ambiente.derrubar(ambiente.sessaoSagas);
    }

    private void conferirMensagem(String fila, double tipo) throws Exception {
        ambiente.conferirMensagem(fila, tipo, idSessao, Map.of(
            "idPagamento", idPagamento,
            "idReservaHotel", idHotel,
            "idReservaVooIda", idVooIda,
            "idReservaVooVolta", idVooVolta
        ));
    }

    private Object statusDaSessao() {
        return ambiente.coluna("sessaocompra", "select status from sessao_compra where id = ?", idSessao);
    }

    private Object statusDoPagamento() {
        return ambiente.coluna("interno_pagamento", "select status from pagamentos where id = ?", idPagamento);
    }

    private Object statusDaTransacao() {
        return ambiente.coluna("externo_pagamento", "select status from pagamentos where id = ?", idTransacao);
    }

}

package com.derso.arquitetura.sessaocompra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.derso.arquitetura.sagas.Messaging;
import com.fasterxml.jackson.databind.JsonNode;

// Compra ponta a ponta com os serviços reais em container: sessão → pré-reservas → pagamento no
// gateway → webhook → SAGA (pagamento → hotel → voo-ida → voo-volta → sessaocompra).
// Cada serviço sobe só na fase em que trabalha e cai depois: a mensagem espera o próximo consumidor
// na fila durável, e o pico fica em 3 JVMs (docs/testing-strategy.md#serviços-em-fases).
// Narrativa: target/roteiro-caminho-feliz.txt. Sistema: só roda com -Psistema (docs/testing-strategy.md).
@Tag("sistema")
class SagaCaminhoFelizSistemaTest {

    private static AmbienteSaga ambiente;
    private static RoteiroTesteIntegrado roteiro;

    private RestClient cliente;
    private UUID idSessao;
    private UUID idHotel;
    private UUID idVooIda;
    private UUID idVooVolta;
    private UUID idPagamento;

    @BeforeAll
    static void montar() {
        ambiente = new AmbienteSaga("roteiro-caminho-feliz.txt");
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
    void compraPagaPercorreACadeiaEReservaAViagem() throws Exception {
        try {
            ambiente.subirInfra();
            criarSessao();
            preReservarHotel();
            preReservarVoos();
            pagar();
            noPagamento();
            noHotel();
            noVooIda();
            noVooVolta();
            naSessaoCompra();
            roteiro.etapa("Fim: viagem reservada, nenhuma mensagem na DLQ");
        } catch (Throwable erro) {
            roteiro.falha(erro);
            throw erro;
        }
    }

    private void criarSessao() {
        roteiro.etapa("1. Sessão de compra");
        ambiente.subir(ambiente.sessaoWeb);

        UUID idCliente = UUID.randomUUID();
        // o teste faz o papel de `clientes`: JWT assinado com o par de teste, cuja pública o container recebeu
        String token = ChavesJwtDeTeste.emissorConfiavel().generateToken(idCliente.toString(), idCliente + "@teste.com", "CLIENTE");
        cliente = RestClient.builder()
            .baseUrl(ambiente.url(ambiente.sessaoWeb, 8080))
            .defaultHeader("Authorization", "Bearer " + token)
            .build();

        roteiro.fazendo("POST /sessoes como cliente " + idCliente);
        JsonNode criada = json(cliente.post().uri("/sessoes").retrieve().body(String.class));
        idSessao = UUID.fromString(criada.get("idSessao").asText());
        roteiro.ok("sessão " + idSessao + " criada");

        ambiente.conferir("sessaocompra", "status da sessão", "INICIADA", statusDaSessao());
    }

    private void preReservarHotel() {
        roteiro.etapa("2. Pré-reserva do hotel");
        ambiente.subir(ambiente.hotelExterno);
        ambiente.subir(ambiente.hotelWeb);

        roteiro.fazendo("PUT /sessoes/" + idSessao + "/hotel");
        cliente.put().uri("/sessoes/{id}/hotel", idSessao).retrieve().toBodilessEntity();
        idHotel = (UUID) ambiente.coluna("sessaocompra", "select id_reserva_hotel from sessao_compra where id = ?", idSessao);
        roteiro.ok("sessão aponta pra reserva de hotel " + idHotel);
        ambiente.conferirReserva("interno_hotel", "externo_hotel", idHotel, "PRE_RESERVADA", false);

        ambiente.derrubar(ambiente.hotelWeb);
        ambiente.derrubar(ambiente.hotelExterno);
    }

    private void preReservarVoos() {
        roteiro.etapa("3. Pré-reserva dos voos");
        ambiente.subir(ambiente.vooExterno);
        ambiente.subir(ambiente.vooWeb);

        roteiro.fazendo("PUT /sessoes/" + idSessao + "/voo-ida");
        cliente.put().uri("/sessoes/{id}/voo-ida", idSessao).retrieve().toBodilessEntity();
        idVooIda = (UUID) ambiente.coluna("sessaocompra", "select id_reserva_voo_ida from sessao_compra where id = ?", idSessao);
        roteiro.ok("sessão aponta pra reserva de ida " + idVooIda);
        ambiente.conferirReserva("interno_voo", "externo_voo", idVooIda, "PRE_RESERVADA", false);

        roteiro.fazendo("PUT /sessoes/" + idSessao + "/voo-volta");
        cliente.put().uri("/sessoes/{id}/voo-volta", idSessao).retrieve().toBodilessEntity();
        idVooVolta = (UUID) ambiente.coluna("sessaocompra", "select id_reserva_voo_volta from sessao_compra where id = ?", idSessao);
        roteiro.ok("sessão aponta pra reserva de volta " + idVooVolta);
        ambiente.conferirReserva("interno_voo", "externo_voo", idVooVolta, "PRE_RESERVADA", false);

        ambiente.derrubar(ambiente.vooWeb);
        ambiente.derrubar(ambiente.vooExterno);
    }

    private void pagar() throws Exception {
        roteiro.etapa("4. Pagamento, até o webhook publicar o início da SAGA");
        ambiente.subir(ambiente.pagamentoExterno);
        ambiente.subir(ambiente.pagamentoWeb);

        roteiro.fazendo("PUT /sessoes/" + idSessao + "/iniciando-pagamento");
        JsonNode inicio = json(cliente.put().uri("/sessoes/{id}/iniciando-pagamento", idSessao).retrieve().body(String.class));
        assertEquals("PAGAR", inicio.get("etapa").asText(), "etapa devolvida ao front");
        roteiro.ok("etapa PAGAR, URL de pagamento " + inicio.get("urlPagamento").asText());
        ambiente.conferir("sessaocompra", "status da sessão", "EFETUANDO_PAGAMENTO", statusDaSessao());

        Map<String, Object> pagamento = ambiente.linha("interno_pagamento",
            "select id, status, id_externo from pagamentos where id_sessao_compra = ?", idSessao);
        idPagamento = (UUID) pagamento.get("id");
        UUID idTransacao = (UUID) pagamento.get("id_externo");
        ambiente.conferir("interno_pagamento", "pagamento " + idPagamento, "AGUARDANDO_PAGAMENTO", pagamento.get("status"));
        ambiente.conferir("externo_pagamento", "transação " + idTransacao, "PENDENTE", statusDaTransacao(idTransacao));

        // o usuário pagando pela URL; o gateway avisa o webhook antes de responder
        roteiro.fazendo("POST /pagar/" + idTransacao + " no gateway");
        RestClient.create(ambiente.url(ambiente.pagamentoExterno, 8086)).post().uri("/pagar/{id}", idTransacao).retrieve().toBodilessEntity();
        ambiente.conferir("externo_pagamento", "transação " + idTransacao, "PAGO", statusDaTransacao(idTransacao));

        Map<String, Object> pago = ambiente.linha("interno_pagamento",
            "select status, sessao_pendente, fila_pendente from pagamentos where id = ?", idPagamento);
        ambiente.conferir("interno_pagamento", "pagamento " + idPagamento, "PAGO", pago.get("status"));
        ambiente.conferir("interno_pagamento", "intenção de avisar a sessão pendente", false, pago.get("sessao_pendente"));
        ambiente.conferir("interno_pagamento", "intenção de publicar na fila pendente", false, pago.get("fila_pendente"));
        ambiente.conferir("sessaocompra", "status da sessão", "PAGAMENTO_EFETUADO", statusDaSessao());

        ambiente.derrubar(ambiente.pagamentoWeb);
        ambiente.derrubar(ambiente.pagamentoExterno);
        ambiente.derrubar(ambiente.sessaoWeb);
        conferirMensagem("pagamento");
    }

    private void noPagamento() throws Exception {
        roteiro.etapa("5. Nó pagamento");
        ambiente.subir(ambiente.pagamentoSagas);
        ambiente.esperarFila("pagamento", 0);
        ambiente.esperarFila("hotel", 1);
        ambiente.derrubar(ambiente.pagamentoSagas);
        conferirMensagem("hotel");
    }

    private void noHotel() throws Exception {
        roteiro.etapa("6. Nó hotel");
        ambiente.subir(ambiente.hotelExterno);
        ambiente.subir(ambiente.hotelSagas);
        ambiente.esperarFila("hotel", 0);
        ambiente.esperarFila("voo-ida", 1);
        ambiente.conferirReserva("interno_hotel", "externo_hotel", idHotel, "RESERVADA", true);
        ambiente.derrubar(ambiente.hotelSagas);
        ambiente.derrubar(ambiente.hotelExterno);
        conferirMensagem("voo-ida");
    }

    private void noVooIda() throws Exception {
        roteiro.etapa("7. Nó voo-ida");
        ambiente.subir(ambiente.vooExterno);
        ambiente.subir(ambiente.vooIdaSagas);
        ambiente.esperarFila("voo-ida", 0);
        ambiente.esperarFila("voo-volta", 1);
        ambiente.conferirReserva("interno_voo", "externo_voo", idVooIda, "RESERVADA", true);
        ambiente.derrubar(ambiente.vooIdaSagas);
        ambiente.derrubar(ambiente.vooExterno);
        conferirMensagem("voo-volta");
    }

    private void noVooVolta() throws Exception {
        roteiro.etapa("8. Nó voo-volta");
        ambiente.subir(ambiente.vooExterno);
        ambiente.subir(ambiente.vooVoltaSagas);
        ambiente.esperarFila("voo-volta", 0);
        ambiente.esperarFila("sessaocompra", 1);
        ambiente.conferirReserva("interno_voo", "externo_voo", idVooVolta, "RESERVADA", true);
        ambiente.derrubar(ambiente.vooVoltaSagas);
        ambiente.derrubar(ambiente.vooExterno);
        conferirMensagem("sessaocompra");
    }

    private void naSessaoCompra() throws Exception {
        roteiro.etapa("9. Nó sessaocompra (fim da cadeia)");
        ambiente.subir(ambiente.sessaoSagas);
        ambiente.esperarFila("sessaocompra", 0);
        ambiente.conferir("sessaocompra", "status da sessão", "VIAGEM_RESERVADA", statusDaSessao());
        ambiente.conferirDlqVazia();
        ambiente.derrubar(ambiente.sessaoSagas);
    }

    private void conferirMensagem(String fila) throws Exception {
        ambiente.conferirMensagem(fila, Messaging.EXECUTE, idSessao, Map.of(
            "idPagamento", idPagamento,
            "idReservaHotel", idHotel,
            "idReservaVooIda", idVooIda,
            "idReservaVooVolta", idVooVolta
        ));
    }

    private Object statusDaSessao() {
        return ambiente.coluna("sessaocompra", "select status from sessao_compra where id = ?", idSessao);
    }

    private Object statusDaTransacao(UUID idTransacao) {
        return ambiente.coluna("externo_pagamento", "select status from pagamentos where id = ?", idTransacao);
    }

    private static JsonNode json(String corpo) {
        try {
            return AmbienteSaga.JSON.readTree(corpo);
        } catch (Exception e) {
            throw new IllegalStateException("JSON inválido: " + corpo, e);
        }
    }

}

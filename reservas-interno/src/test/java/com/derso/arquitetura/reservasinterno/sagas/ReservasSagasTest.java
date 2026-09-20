package com.derso.arquitetura.reservasinterno.sagas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import com.derso.arquitetura.reservasinterno.ReservasExternoService;
import com.derso.arquitetura.reservasinterno.ReservasRepository;
import com.derso.arquitetura.reservasinterno.entity.Reserva;
import com.derso.arquitetura.sagas.Encaminhamento;
import com.derso.arquitetura.sagas.Messaging;
import com.derso.arquitetura.sagas.ResultadoHandler;
import com.derso.arquitetura.sagas.RetornoBroker;

class ReservasSagasTest {

    private final ReservasExternoService servicoExterno = mock(ReservasExternoService.class);
    private final ReservasRepository repositorio = mock(ReservasRepository.class);
    private final Messaging sagas = mock(Messaging.class);
    private final ReservasSagas reservasSagas;

    ReservasSagasTest() throws IOException {
        reservasSagas = new ReservasSagas(sagas, servicoExterno, repositorio);
        reservasSagas.estaFila = "hotel";
    }

    @Test
    void confirmarComSucessoChamaExternoEDevolveAckParaFrente() {
        UUID idExterno = UUID.randomUUID();
        Reserva reserva = new Reserva(UUID.randomUUID(), idExterno);

        ResultadoHandler resultado = reservasSagas.confirmar(reserva, "rastreio-1");

        verify(servicoExterno).confirmar(idExterno);
        assertEquals(RetornoBroker.ACK, resultado.retornoBroker());
        assertEquals(Encaminhamento.PARA_FRENTE, resultado.encaminhamento());
    }

    @Test
    void confirmarComNotFoundEhFalhaDeNegocioDevolveAckParaTras() {
        UUID idExterno = UUID.randomUUID();
        Reserva reserva = new Reserva(UUID.randomUUID(), idExterno);

        doThrow(notFound()).when(servicoExterno).confirmar(idExterno);

        ResultadoHandler resultado = reservasSagas.confirmar(reserva, "rastreio-2");

        assertEquals(RetornoBroker.ACK, resultado.retornoBroker());
        assertEquals(Encaminhamento.PARA_TRAS, resultado.encaminhamento());
    }

    @Test
    void confirmarComFalhaDeInfraEhFalhaGeralDevolveAckSemEncaminhamento() {
        UUID idExterno = UUID.randomUUID();
        Reserva reserva = new Reserva(UUID.randomUUID(), idExterno);

        doThrow(HttpServerErrorException.create(
            HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], null
        )).when(servicoExterno).confirmar(idExterno);

        ResultadoHandler resultado = reservasSagas.confirmar(reserva, "rastreio-3");

        assertEquals(RetornoBroker.ACK, resultado.retornoBroker());
        assertEquals(Encaminhamento.NENHUM, resultado.encaminhamento());
    }

    @Test
    void confirmarComTimeoutTambemEhFalhaGeral() {
        UUID idExterno = UUID.randomUUID();
        Reserva reserva = new Reserva(UUID.randomUUID(), idExterno);

        doThrow(new RuntimeException("timeout")).when(servicoExterno).confirmar(idExterno);

        ResultadoHandler resultado = reservasSagas.confirmar(reserva, "rastreio-4");

        assertEquals(RetornoBroker.ACK, resultado.retornoBroker());
        assertEquals(Encaminhamento.NENHUM, resultado.encaminhamento());
    }

    @Test
    void cancelarComSucessoDevolveAckParaTras() {
        UUID idExterno = UUID.randomUUID();
        Reserva reserva = new Reserva(UUID.randomUUID(), idExterno);

        ResultadoHandler resultado = reservasSagas.cancelar(reserva, "rastreio-5");

        verify(servicoExterno).cancelar(idExterno);
        assertEquals(RetornoBroker.ACK, resultado.retornoBroker());
        assertEquals(Encaminhamento.PARA_TRAS, resultado.encaminhamento());
    }

    @Test
    void cancelarComFalhaAindaAssimDevolveAckParaTras_melhorEsforco() {
        UUID idExterno = UUID.randomUUID();
        Reserva reserva = new Reserva(UUID.randomUUID(), idExterno);

        doThrow(new RuntimeException("reservas-externo indisponível")).when(servicoExterno).cancelar(idExterno);

        ResultadoHandler resultado = reservasSagas.cancelar(reserva, "rastreio-6");

        assertEquals(RetornoBroker.ACK, resultado.retornoBroker());
        assertEquals(Encaminhamento.PARA_TRAS, resultado.encaminhamento());
    }

    @Test
    void idReservaDaMensagemAindaNaoImplementado() {
        // Trava o comportamento atual (stub proposital) — ver TODO no próprio método.
        // Este teste deve mudar quando o payload da mensagem ganhar o campo de verdade.
        assertThrows(UnsupportedOperationException.class, () -> reservasSagas.idReservaDaMensagem(Map.of()));
    }

    private static HttpClientErrorException.NotFound notFound() {
        return (HttpClientErrorException.NotFound) HttpClientErrorException.create(
            HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null
        );
    }
}

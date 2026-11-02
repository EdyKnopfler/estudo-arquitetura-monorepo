package com.derso.arquitetura.reservasinterno.sagas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
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

    ReservasSagasTest() {
        reservasSagas = new ReservasSagas(new ConsumersSaga(List.of()), sagas, servicoExterno, repositorio);
    }

    @Test
    void confirmarComSucessoChamaExternoEDevolveAckParaFrente() {
        UUID idExterno = UUID.randomUUID();
        Reserva reserva = new Reserva(UUID.randomUUID(), idExterno);

        ResultadoHandler resultado = reservasSagas.confirmar(reserva, "hotel", "rastreio-1");

        verify(servicoExterno).confirmar(idExterno);
        verify(repositorio).marcarReservada(reserva.getId());
        assertEquals(RetornoBroker.ACK, resultado.retornoBroker());
        assertEquals(Encaminhamento.PARA_FRENTE, resultado.encaminhamento());
    }

    @Test
    void confirmarComNotFoundEhFalhaDeNegocioDevolveAckParaTras() {
        UUID idExterno = UUID.randomUUID();
        Reserva reserva = new Reserva(UUID.randomUUID(), idExterno);

        doThrow(notFound()).when(servicoExterno).confirmar(idExterno);

        ResultadoHandler resultado = reservasSagas.confirmar(reserva, "hotel", "rastreio-2");

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

        ResultadoHandler resultado = reservasSagas.confirmar(reserva, "hotel", "rastreio-3");

        assertEquals(RetornoBroker.ACK, resultado.retornoBroker());
        assertEquals(Encaminhamento.NENHUM, resultado.encaminhamento());
    }

    @Test
    void confirmarComTimeoutTambemEhFalhaGeral() {
        UUID idExterno = UUID.randomUUID();
        Reserva reserva = new Reserva(UUID.randomUUID(), idExterno);

        doThrow(new RuntimeException("timeout")).when(servicoExterno).confirmar(idExterno);

        ResultadoHandler resultado = reservasSagas.confirmar(reserva, "hotel", "rastreio-4");

        assertEquals(RetornoBroker.ACK, resultado.retornoBroker());
        assertEquals(Encaminhamento.NENHUM, resultado.encaminhamento());
    }

    @Test
    void reverterComSucessoDesconfirmaVoltaPraPreReservaEDevolveAckParaTras() {
        UUID idExterno = UUID.randomUUID();
        Reserva reserva = new Reserva(UUID.randomUUID(), idExterno);

        ResultadoHandler resultado = reservasSagas.reverter(reserva, "hotel", "rastreio-5");

        verify(servicoExterno).desconfirmar(idExterno);
        verify(repositorio).marcarPreReservada(reserva.getId());
        assertEquals(RetornoBroker.ACK, resultado.retornoBroker());
        assertEquals(Encaminhamento.PARA_TRAS, resultado.encaminhamento());
    }

    @Test
    void reverterJaDesconfirmadaNoExternoVoltaPraPreReservaMesmoAssim() {
        UUID idExterno = UUID.randomUUID();
        Reserva reserva = new Reserva(UUID.randomUUID(), idExterno);
        doThrow(notFound()).when(servicoExterno).desconfirmar(idExterno);

        ResultadoHandler resultado = reservasSagas.reverter(reserva, "hotel", "rastreio-6");

        verify(repositorio).marcarPreReservada(reserva.getId());
        assertEquals(Encaminhamento.PARA_TRAS, resultado.encaminhamento());
    }

    @Test
    void reverterComFalhaAmbiguaNaoMexeNoStatusMasSegueParaTras() {
        UUID idExterno = UUID.randomUUID();
        Reserva reserva = new Reserva(UUID.randomUUID(), idExterno);
        doThrow(new RuntimeException("reservas-externo indisponível")).when(servicoExterno).desconfirmar(idExterno);

        ResultadoHandler resultado = reservasSagas.reverter(reserva, "hotel", "rastreio-7");

        verify(repositorio, never()).marcarPreReservada(any());
        assertEquals(RetornoBroker.ACK, resultado.retornoBroker());
        assertEquals(Encaminhamento.PARA_TRAS, resultado.encaminhamento());
    }

    @Test
    void idReservaDaMensagemLeOCampoDoNo() {
        UUID ida = UUID.randomUUID();
        UUID volta = UUID.randomUUID();
        // como chega do broker: UUID serializado como string
        Map<String, Object> mensagem = Map.of("idReservaVooIda", ida.toString(), "idReservaVooVolta", volta.toString());

        assertEquals(ida, ReservasSagas.idReservaDaMensagem(mensagem, "idReservaVooIda"));
        assertEquals(volta, ReservasSagas.idReservaDaMensagem(mensagem, "idReservaVooVolta"));
    }

    @Test
    void idReservaAusenteNaMensagemLanca() {
        assertThrows(IllegalArgumentException.class,
            () -> ReservasSagas.idReservaDaMensagem(Map.of("idReservaHotel", UUID.randomUUID().toString()), "idReservaVooIda"));
    }

    private static HttpClientErrorException.NotFound notFound() {
        return (HttpClientErrorException.NotFound) HttpClientErrorException.create(
            HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null
        );
    }
}

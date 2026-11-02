package com.derso.arquitetura.pagamentointerno.sagas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.derso.arquitetura.pagamentointerno.PagamentoExternoService;
import com.derso.arquitetura.pagamentointerno.PagamentoRepository;
import com.derso.arquitetura.pagamentointerno.dto.CriarTransacaoResponse;
import com.derso.arquitetura.pagamentointerno.entity.Pagamento;
import com.derso.arquitetura.pagamentointerno.entity.StatusPagamento;
import com.derso.arquitetura.sagas.Encaminhamento;
import com.derso.arquitetura.sagas.Messaging;
import com.derso.arquitetura.sagas.ResultadoHandler;
import com.derso.arquitetura.sagas.RetornoBroker;

class PagamentoSagasTest {

    private final PagamentoExternoService servicoExterno = mock(PagamentoExternoService.class);
    private final PagamentoRepository repositorio = mock(PagamentoRepository.class);
    private final PagamentoSagas pagamentoSagas = new PagamentoSagas(mock(Messaging.class), servicoExterno, repositorio);

    @Test
    void reverterPagoEstornaReabreALinhaEDevolveAckParaTras() {
        Pagamento pagamento = pagamento(StatusPagamento.PAGO);
        UUID id = pagamento.getId();
        UUID idExterno = pagamento.getIdExterno();
        when(servicoExterno.estornar(idExterno)).thenReturn(situacao("ESTORNADO"));

        ResultadoHandler resultado = pagamentoSagas.reverter(pagamento, "rastreio-1");

        verify(repositorio).reabrirAposEstorno(eq(id), eq(idExterno), any());
        assertEquals(RetornoBroker.ACK, resultado.retornoBroker());
        assertEquals(Encaminhamento.PARA_TRAS, resultado.encaminhamento());
    }

    @Test
    void reverterJaReabertoNaoEstornaDeNovoESegueParaTras() {
        Pagamento pagamento = pagamento(StatusPagamento.CRIANDO);

        ResultadoHandler resultado = pagamentoSagas.reverter(pagamento, "rastreio-2");

        verify(servicoExterno, never()).estornar(any());
        assertEquals(Encaminhamento.PARA_TRAS, resultado.encaminhamento());
    }

    // Desfecho provisório — erro catastrófico em aberto
    @Test
    void falhaNoEstornoVaiPraFilaDeErrosSemReverterNada() {
        Pagamento pagamento = pagamento(StatusPagamento.PAGO);
        when(servicoExterno.estornar(pagamento.getIdExterno())).thenThrow(new RuntimeException("gateway fora"));

        ResultadoHandler resultado = pagamentoSagas.reverter(pagamento, "rastreio-3");

        verify(repositorio, never()).reabrirAposEstorno(any(), any(), any());
        assertEquals(RetornoBroker.NACK_DLQ, resultado.retornoBroker());
        assertEquals(Encaminhamento.NENHUM, resultado.encaminhamento());
    }

    @Test
    void estornoSemEstornadoNoGatewayTambemVaiPraFilaDeErros() {
        Pagamento pagamento = pagamento(StatusPagamento.PAGO);
        when(servicoExterno.estornar(pagamento.getIdExterno())).thenReturn(situacao("PAGO"));

        ResultadoHandler resultado = pagamentoSagas.reverter(pagamento, "rastreio-4");

        verify(repositorio, never()).reabrirAposEstorno(any(), any(), any());
        assertEquals(RetornoBroker.NACK_DLQ, resultado.retornoBroker());
    }

    @Test
    void idPagamentoDaMensagemLeOCampo() {
        UUID id = UUID.randomUUID();

        assertEquals(id, PagamentoSagas.idPagamentoDaMensagem(Map.of("idPagamento", id.toString())));
    }

    @Test
    void idPagamentoAusenteNaMensagemLanca() {
        assertThrows(IllegalArgumentException.class, () -> PagamentoSagas.idPagamentoDaMensagem(Map.of()));
    }

    private static Pagamento pagamento(StatusPagamento status) {
        Pagamento pagamento = mock(Pagamento.class);
        when(pagamento.getId()).thenReturn(UUID.randomUUID());
        when(pagamento.getIdExterno()).thenReturn(UUID.randomUUID());
        when(pagamento.getStatus()).thenReturn(status);
        return pagamento;
    }

    private static CriarTransacaoResponse situacao(String status) {
        return new CriarTransacaoResponse(UUID.randomUUID(), status, null, null);
    }

}

package com.derso.arquitetura.sessaocompra.sagas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.derso.arquitetura.sagas.Encaminhamento;
import com.derso.arquitetura.sagas.Messaging;
import com.derso.arquitetura.sagas.ResultadoHandler;
import com.derso.arquitetura.sagas.RetornoBroker;
import com.derso.arquitetura.sessaocompra.SessaoCompraService;

// Só o mapeamento mensagem → serviço → desfecho; as transições de estado estão em SessaoCompraFluxoTest
class SessaoCompraSagasTest {

    private final SessaoCompraService servico = mock(SessaoCompraService.class);
    private final SessaoCompraSagas sessaoCompraSagas = new SessaoCompraSagas(mock(Messaging.class), servico);

    @Test
    void executeFechaAViagemESemEncaminhamento() {
        UUID id = UUID.randomUUID();
        when(servico.viagemReservada(id)).thenReturn(true);

        ResultadoHandler resultado = sessaoCompraSagas.tratar(mensagem(Messaging.EXECUTE, id));

        verify(servico, never()).voltarAoPedido(any());
        assertEquals(RetornoBroker.ACK, resultado.retornoBroker());
        assertEquals(Encaminhamento.NENHUM, resultado.encaminhamento());
    }

    @Test
    void desfacaVoltaAoPedidoESemEncaminhamento() {
        UUID id = UUID.randomUUID();
        when(servico.voltarAoPedido(id)).thenReturn(true);

        ResultadoHandler resultado = sessaoCompraSagas.tratar(mensagem(Messaging.DESFACA, id));

        verify(servico, never()).viagemReservada(any());
        assertEquals(RetornoBroker.ACK, resultado.retornoBroker());
        assertEquals(Encaminhamento.NENHUM, resultado.encaminhamento());
    }

    // Provisório: caso não tratado vai pra fila de erros sem disparar compensação
    @Test
    void sessaoForaDoEstadoEsperadoVaiPraFilaDeErros() {
        UUID id = UUID.randomUUID();
        when(servico.viagemReservada(id)).thenReturn(false);

        ResultadoHandler resultado = sessaoCompraSagas.tratar(mensagem(Messaging.EXECUTE, id));

        assertEquals(RetornoBroker.NACK_DLQ, resultado.retornoBroker());
        assertEquals(Encaminhamento.NENHUM, resultado.encaminhamento());
    }

    @Test
    void mensagemSemIdDaSessaoLanca() {
        assertThrows(IllegalArgumentException.class, () -> sessaoCompraSagas.tratar(Map.of("tipo", Messaging.EXECUTE)));
    }

    // como chega do broker: UUID serializado como string
    private static Map<String, Object> mensagem(double tipo, UUID idSessao) {
        return Map.of("tipo", tipo, "idSessaoCompra", idSessao.toString());
    }

}

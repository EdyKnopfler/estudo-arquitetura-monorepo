package com.derso.arquitetura.pagamentointerno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.HttpServerErrorException;

import com.derso.arquitetura.pagamentointerno.dto.PagamentoDTO;
import com.derso.arquitetura.pagamentointerno.entity.Pagamento;

class PagamentoServiceTest {

    private final PagamentoRepository repositorio = mock(PagamentoRepository.class);
    private final PagamentoExternoService servicoExterno = mock(PagamentoExternoService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final PagamentoService servico = new PagamentoService(transactionManager, repositorio, servicoExterno);

    @Test
    void criarPagamentoComSucessoSalvaLinhaCompletaEDevolveSoOIdInterno() {
        UUID idExterno = UUID.randomUUID();
        UUID idSessaoCompra = UUID.randomUUID();
        UUID idReservaHotel = UUID.randomUUID();
        UUID idReservaVooIda = UUID.randomUUID();
        UUID idReservaVooVolta = UUID.randomUUID();

        when(servicoExterno.efetuar(anyString(), any(BigDecimal.class))).thenReturn(idExterno);

        PagamentoDTO resultado = servico.criarPagamento(idSessaoCompra, idReservaHotel, idReservaVooIda, idReservaVooVolta);

        ArgumentCaptor<Pagamento> captor = ArgumentCaptor.forClass(Pagamento.class);
        verify(repositorio).save(captor.capture());
        Pagamento salvo = captor.getValue();

        assertEquals(idExterno, salvo.getIdExterno());
        assertEquals(idSessaoCompra, salvo.getIdSessaoCompra());
        assertEquals(idReservaHotel, salvo.getIdReservaHotel());
        assertEquals(idReservaVooIda, salvo.getIdReservaVooIda());
        assertEquals(idReservaVooVolta, salvo.getIdReservaVooVolta());
        assertEquals(salvo.getId(), resultado.id());
    }

    @Test
    void falhaNoServicoExternoPropagaSemPersistirNada() {
        when(servicoExterno.efetuar(anyString(), any(BigDecimal.class))).thenThrow(
            HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], null)
        );

        assertThrows(HttpServerErrorException.class, () ->
            servico.criarPagamento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        );

        // Sem outbox aqui de propósito — a linha só é salva depois do /efetuar responder (ver
        // docs/todo.md, "Dual-write pagamento/reservas"), então falha antes disso não deixa rastro.
        verify(repositorio, never()).save(any());
    }

}

package com.derso.arquitetura.pagamentointerno;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.derso.arquitetura.pagamentointerno.dto.PagamentoDTO;
import com.derso.arquitetura.pagamentointerno.entity.Pagamento;

@Service
public class PagamentoService {

    // TODO método/valor fixos — regra de precificação ainda não existe (checklist de domínio em README.md).
    private static final String METODO_PLACEHOLDER = "cartao";
    private static final BigDecimal VALOR_PLACEHOLDER = new BigDecimal("100.00");

    private final TransactionTemplate transactionTemplate;
    private final PagamentoRepository repositorio;
    private final PagamentoExternoService servicoExterno;

    public PagamentoService(
        PlatformTransactionManager transactionManager,
        PagamentoRepository repositorio,
        PagamentoExternoService servicoExterno
    ) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.repositorio = repositorio;
        this.servicoExterno = servicoExterno;
    }

    public PagamentoDTO criarPagamento(UUID idSessaoCompra, UUID idReservaHotel, UUID idReservaVooIda, UUID idReservaVooVolta) {

        // NUNCA chamamos o serviço externo dentro de uma transação — convenção de ReservasService.

        UUID idExterno = servicoExterno.efetuar(METODO_PLACEHOLDER, VALOR_PLACEHOLDER);

        return transactionTemplate.execute(status -> {
            Pagamento novoPagamento = new Pagamento(idExterno, idSessaoCompra, idReservaHotel, idReservaVooIda, idReservaVooVolta);
            repositorio.save(novoPagamento);
            return new PagamentoDTO(novoPagamento.getId());
        });
    }

}

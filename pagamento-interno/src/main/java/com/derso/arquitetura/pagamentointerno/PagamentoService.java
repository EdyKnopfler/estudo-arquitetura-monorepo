package com.derso.arquitetura.pagamentointerno;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

import com.derso.arquitetura.pagamentointerno.dto.EfetuarPagamentoResponse;
import com.derso.arquitetura.pagamentointerno.dto.PagamentoDTO;
import com.derso.arquitetura.pagamentointerno.entity.Pagamento;
import com.derso.arquitetura.pagamentointerno.entity.StatusPagamento;
import com.derso.arquitetura.webbase.config.BusinessException;

// Desenho (intenção antes do gateway, chave por tentativa): docs/purchase-flow-design.md#criação-do-pagamento
@Service
public class PagamentoService {

    // TODO método/valor fixos — regra de precificação ainda não existe (docs/todo.md).
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
        Pagamento pagamento = transactionTemplate.execute(status -> {
            repositorio.criarSeNaoExistir(
                UUID.randomUUID(), idSessaoCompra, idReservaHotel, idReservaVooIda, idReservaVooVolta, UUID.randomUUID()
            );
            return repositorio.findByIdSessaoCompra(idSessaoCompra).orElseThrow();
        });

        if (pagamento.getStatus() == StatusPagamento.AGUARDANDO_PAGAMENTO) {
            return new PagamentoDTO(pagamento.getId(), pagamento.getUrlPagamento());
        }

        // NUNCA chamamos o serviço externo dentro de uma transação — convenção de ReservasService.
        // Falha ambígua (5xx, timeout) propaga sem mexer na linha: a próxima chamada repete a mesma chave.
        EfetuarPagamentoResponse resposta;
        try {
            resposta = servicoExterno.efetuar(METODO_PLACEHOLDER, VALOR_PLACEHOLDER, pagamento.getChaveIdempotencia());
        } catch (HttpClientErrorException e) {
            transactionTemplate.execute(status ->
                repositorio.trocarChave(pagamento.getId(), pagamento.getChaveIdempotencia(), UUID.randomUUID())
            );
            throw new BusinessException("Gateway recusou a criação do pagamento");
        }

        int linhas = transactionTemplate.execute(status -> repositorio.registrarUrl(
            pagamento.getId(), pagamento.getChaveIdempotencia(), resposta.idTransacao(), resposta.urlPagamento()
        ));

        if (linhas == 0) {
            // Chamada concorrente registrou antes (mesma chave, mesma transação) ou trocou de tentativa.
            Pagamento atual = repositorio.findById(pagamento.getId()).orElseThrow();
            if (atual.getStatus() != StatusPagamento.AGUARDANDO_PAGAMENTO) {
                throw new IllegalStateException("Tentativa de pagamento trocada durante a criação: " + pagamento.getId());
            }
            return new PagamentoDTO(atual.getId(), atual.getUrlPagamento());
        }

        return new PagamentoDTO(pagamento.getId(), resposta.urlPagamento());
    }

}

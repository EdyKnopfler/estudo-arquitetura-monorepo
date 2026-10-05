package com.derso.arquitetura.pagamentointerno;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

import com.derso.arquitetura.pagamentointerno.dto.CriarTransacaoResponse;
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

    // StatusPagamento de pagamento-externo (módulo separado, sem tipos compartilhados)
    private static final String STATUS_EXPIRADO_NO_GATEWAY = "EXPIRADO";

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

        return obterUrl(pagamento, true);
    }

    private PagamentoDTO obterUrl(Pagamento pagamento, boolean podeRenovar) {
        // NUNCA chamamos o serviço externo dentro de uma transação — convenção de ReservasService.
        // Falha ambígua (5xx, timeout) propaga sem mexer na linha: a próxima chamada repete a mesma chave.
        CriarTransacaoResponse resposta;
        try {
            resposta = servicoExterno.criar(METODO_PLACEHOLDER, VALOR_PLACEHOLDER, pagamento.getChaveIdempotencia());
        } catch (HttpClientErrorException e) {
            transactionTemplate.execute(status ->
                repositorio.trocarChave(pagamento.getId(), pagamento.getChaveIdempotencia(), UUID.randomUUID())
            );
            throw new BusinessException("Gateway rejeitou a criação do pagamento");
        }

        if (STATUS_EXPIRADO_NO_GATEWAY.equals(resposta.status())) {
            // Venceu lá antes da URL chegar aqui (ex.: falha ambígua retentada tarde). O PUT só chega
            // com a sessão viva, então abre tentativa nova — o prazo do gateway não importa ao interno.
            if (!podeRenovar) {
                throw new IllegalStateException("Gateway devolveu transação expirada para chave nova: " + pagamento.getId());
            }
            transactionTemplate.execute(status ->
                repositorio.trocarChave(pagamento.getId(), pagamento.getChaveIdempotencia(), UUID.randomUUID())
            );
            Pagamento atual = repositorio.findById(pagamento.getId()).orElseThrow();
            if (atual.getStatus() == StatusPagamento.AGUARDANDO_PAGAMENTO) {
                return new PagamentoDTO(atual.getId(), atual.getUrlPagamento());
            }
            return obterUrl(atual, false);
        }

        if (resposta.urlPagamento() == null) {
            throw new IllegalStateException("Gateway não devolveu URL de pagamento: " + pagamento.getId());
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

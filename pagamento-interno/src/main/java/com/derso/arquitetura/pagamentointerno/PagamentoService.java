package com.derso.arquitetura.pagamentointerno;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
import com.derso.arquitetura.webbase.http.TimeoutHttp;

// Desenho (intenção antes do gateway, chave por tentativa): docs/purchase-flow-design.md#criação-do-pagamento
@Service
public class PagamentoService {

    // TODO método/valor fixos — regra de precificação ainda não existe (docs/todo.md).
    private static final String METODO_PLACEHOLDER = "cartao";
    private static final BigDecimal VALOR_PLACEHOLDER = new BigDecimal("100.00");

    // StatusPagamento de pagamento-externo (módulo separado, sem tipos compartilhados)
    private static final String STATUS_EXPIRADO_NO_GATEWAY = "EXPIRADO";
    private static final String STATUS_CANCELADO_NO_GATEWAY = "CANCELADO";
    private static final String STATUS_PAGO_NO_GATEWAY = "PAGO";

    // Link a menos disso do vencimento do gateway é renovado, em vez de entregue
    private static final Duration MARGEM_RENOVACAO = Duration.ofMinutes(3);

    // O máximo que uma chamada ao gateway dura (PagamentoExternoService usa TimeoutHttp.padrao):
    // depois disso, a chamada em voo é dada como morta (ex.: processo caiu)
    private static final Duration JANELA_TENTATIVA = TimeoutHttp.CONEXAO.plus(TimeoutHttp.LEITURA);

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
            if (Instant.now().plus(MARGEM_RENOVACAO).isBefore(pagamento.getVencimento())) {
                return PagamentoDTO.de(pagamento);
            }
            return renovar(pagamento);
        }

        return obterUrl(pagamento, true);
    }

    // URL só é substituída depois de cancelada ou vencida no gateway.
    // Falha ambígua no cancelar propaga sem mexer na linha: a próxima chamada cancela de novo.
    private PagamentoDTO renovar(Pagamento pagamento) {
        CriarTransacaoResponse cancelamento;
        try {
            cancelamento = servicoExterno.cancelar(pagamento.getIdExterno());
        } catch (HttpClientErrorException.Conflict e) {
            // Gateway recusa cancelar transação paga; a consulta confirma. Só responde — a linha
            // não muda, quem fecha o estado é o webhook (docs/purchase-flow-design.md#pagamento-da-sessão-tentativa-trocável)
            CriarTransacaoResponse situacao = servicoExterno.consultar(pagamento.getIdExterno());
            if (!STATUS_PAGO_NO_GATEWAY.equals(situacao.status())) {
                throw new IllegalStateException("Cancelamento recusado sem pagamento para " + pagamento.getId() + ": " + situacao.status());
            }
            return new PagamentoDTO(pagamento.getId(), StatusPagamento.PAGO, null, null);
        }

        // Só o caminho feliz; outro desfecho falha alto até ganhar tratamento — docs/state-driven-endpoints.md
        if (!STATUS_CANCELADO_NO_GATEWAY.equals(cancelamento.status()) && !STATUS_EXPIRADO_NO_GATEWAY.equals(cancelamento.status())) {
            throw new IllegalStateException("Desfecho do cancelamento não tratado para " + pagamento.getId() + ": " + cancelamento.status());
        }

        transactionTemplate.execute(status ->
            repositorio.renovarTentativa(pagamento.getId(), pagamento.getIdExterno(), UUID.randomUUID())
        );
        // Sem linha afetada, uma chamada concorrente já renovou: segue do estado que ela deixou
        Pagamento atual = repositorio.findById(pagamento.getId()).orElseThrow();
        if (atual.getStatus() == StatusPagamento.AGUARDANDO_PAGAMENTO) {
            return PagamentoDTO.de(atual);
        }
        return obterUrl(atual, false);
    }

    private PagamentoDTO obterUrl(Pagamento pagamento, boolean podeRenovar) {
        Instant agora = Instant.now();
        int ocupada = transactionTemplate.execute(status -> repositorio.ocuparTentativa(
            pagamento.getId(), pagamento.getChaveIdempotencia(), agora, agora.minus(JANELA_TENTATIVA)
        ));
        if (ocupada == 0) {
            // Outra chamada em voo, ou ela já concluiu: quem pede decide se espera e repete
            Pagamento atual = repositorio.findById(pagamento.getId()).orElseThrow();
            if (atual.getStatus() == StatusPagamento.AGUARDANDO_PAGAMENTO) {
                return PagamentoDTO.de(atual);
            }
            return new PagamentoDTO(pagamento.getId(), StatusPagamento.CRIANDO, null, null);
        }

        // NUNCA chamamos o serviço externo dentro de uma transação — convenção de ReservasService.
        // Falha ambígua (5xx, timeout) só libera a janela: a próxima chamada repete a mesma chave.
        CriarTransacaoResponse resposta;
        try {
            resposta = servicoExterno.criar(METODO_PLACEHOLDER, VALOR_PLACEHOLDER, pagamento.getChaveIdempotencia());
        } catch (HttpClientErrorException e) {
            transactionTemplate.execute(status ->
                repositorio.trocarChave(pagamento.getId(), pagamento.getChaveIdempotencia(), UUID.randomUUID())
            );
            throw new BusinessException("Gateway rejeitou a criação do pagamento");
        } catch (RuntimeException e) {
            liberarTentativa(pagamento);
            throw e;
        }

        if (STATUS_EXPIRADO_NO_GATEWAY.equals(resposta.status())) {
            // Venceu lá antes da URL chegar aqui (ex.: falha ambígua retentada tarde) — expirada no
            // gateway não encerra o pagamento da sessão: abre tentativa nova.
            if (!podeRenovar) {
                throw new IllegalStateException("Gateway devolveu transação expirada para chave nova: " + pagamento.getId());
            }
            transactionTemplate.execute(status ->
                repositorio.trocarChave(pagamento.getId(), pagamento.getChaveIdempotencia(), UUID.randomUUID())
            );
            Pagamento atual = repositorio.findById(pagamento.getId()).orElseThrow();
            if (atual.getStatus() == StatusPagamento.AGUARDANDO_PAGAMENTO) {
                return PagamentoDTO.de(atual);
            }
            return obterUrl(atual, false);
        }

        if (resposta.urlPagamento() == null || resposta.vencimento() == null) {
            liberarTentativa(pagamento);
            throw new IllegalStateException("Gateway não devolveu URL e vencimento do pagamento: " + pagamento.getId());
        }

        int linhas = transactionTemplate.execute(status -> repositorio.registrarUrl(
            pagamento.getId(), pagamento.getChaveIdempotencia(), resposta.idTransacao(), resposta.urlPagamento(),
            resposta.vencimento()
        ));

        if (linhas == 0) {
            // Chamada concorrente registrou antes (mesma chave, mesma transação) ou trocou de tentativa.
            Pagamento atual = repositorio.findById(pagamento.getId()).orElseThrow();
            if (atual.getStatus() != StatusPagamento.AGUARDANDO_PAGAMENTO) {
                throw new IllegalStateException("Tentativa de pagamento trocada durante a criação: " + pagamento.getId());
            }
            return PagamentoDTO.de(atual);
        }

        return new PagamentoDTO(
            pagamento.getId(), StatusPagamento.AGUARDANDO_PAGAMENTO, resposta.urlPagamento(), resposta.vencimento()
        );
    }

    private void liberarTentativa(Pagamento pagamento) {
        transactionTemplate.execute(status -> repositorio.liberarTentativa(pagamento.getId(), pagamento.getChaveIdempotencia()));
    }

}

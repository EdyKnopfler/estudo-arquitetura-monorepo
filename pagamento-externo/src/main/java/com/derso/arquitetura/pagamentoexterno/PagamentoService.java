package com.derso.arquitetura.pagamentoexterno;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.derso.arquitetura.pagamentoexterno.app.ResultadoPagar;
import com.derso.arquitetura.pagamentoexterno.app.ResultadoSimulado;
import com.derso.arquitetura.pagamentoexterno.app.Simulador;
import com.derso.arquitetura.pagamentoexterno.entity.Pagamento;
import com.derso.arquitetura.pagamentoexterno.entity.StatusPagamento;
import com.derso.arquitetura.pagamentoexterno.webhook.WebhookService;
import com.derso.arquitetura.webbase.config.BusinessException;

import jakarta.persistence.EntityNotFoundException;

// Ruim de propósito — docs/purchase-flow-design.md#premissas-do-gateway-simulado-pagamento-externo
@Service
public class PagamentoService {

    private static final Logger log = LoggerFactory.getLogger(PagamentoService.class);

    private final TransactionTemplate transactionTemplate;
    private final PagamentoRepository repositorio;
    private final Simulador simulador;
    private final WebhookService webhook;

    public PagamentoService(
        PlatformTransactionManager transactionManager,
        PagamentoRepository repositorio,
        Simulador simulador,
        WebhookService webhook
    ) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.repositorio = repositorio;
        this.simulador = simulador;
        this.webhook = webhook;
    }

    // Par (cliente, chave) existente devolve a transação como está, sem criar outra
    public Pagamento criar(String idCliente, UUID chave, String metodo, BigDecimal valor) {
        ResultadoSimulado resultado = simulador.decidir();

        if (resultado == ResultadoSimulado.FALHA_ANTES_DE_GRAVAR) {
            throw new RuntimeException("Falha simulada antes de gravar: " + UUID.randomUUID());
        }

        Pagamento pagamento = transactionTemplate.execute(status -> {
            repositorio.criarSeNaoExistir(UUID.randomUUID(), idCliente, chave, metodo, valor);
            return repositorio.findByIdClienteAndChaveIdempotencia(idCliente, chave).orElseThrow();
        });

        if (resultado == ResultadoSimulado.FALHA_DEPOIS_DE_GRAVAR) {
            throw new RuntimeException("Falha simulada depois de gravar: " + UUID.randomUUID());
        }

        return pagamento;
    }

    // Já paga responde pago sem reenviar o webhook; recusa não muda a transação
    public Pagamento pagar(UUID idTransacao) {
        Pagamento pagamento = buscar(idTransacao);

        switch (pagamento.statusEm(Instant.now())) {
            case PAGO -> {
                return pagamento;
            }
            case EXPIRADO -> throw new BusinessException("Transação expirada: " + idTransacao);
            case CANCELADO -> throw new BusinessException("Transação cancelada: " + idTransacao);
            case PENDENTE -> { }
        }

        ResultadoPagar resultado = simulador.decidirPagar();

        if (resultado == ResultadoPagar.RECUSADO) {
            avisar(pagamento, "RECUSADO");
            throw new BusinessException("Pagamento recusado (simulado)");
        }

        Instant agora = Instant.now();
        int linhas = transactionTemplate.execute(status ->
            repositorio.marcarPago(idTransacao, agora.minus(Pagamento.PRAZO))
        );

        if (linhas == 0) {
            // pagamento concorrente chegou antes (já avisou), ou o prazo venceu desde a leitura
            Pagamento atual = buscar(idTransacao);
            if (atual.statusEm(agora) == StatusPagamento.PAGO) {
                return atual;
            }
            throw new BusinessException("Transação expirada: " + idTransacao);
        }

        if (resultado == ResultadoPagar.PAGO_SEM_AVISO) {
            throw new RuntimeException("Falha simulada depois de marcar pago: " + UUID.randomUUID());
        }

        avisar(pagamento, "PAGO");
        return buscar(idTransacao);
    }

    // Só o cliente que criou enxerga a transação
    public Pagamento consultar(String idCliente, UUID idTransacao) {
        return repositorio.findByIdAndIdCliente(idTransacao, idCliente)
            .orElseThrow(() -> new EntityNotFoundException("Transação não encontrada: " + idTransacao));
    }

    // Encerra a pendente antes do prazo; paga não se cancela — quem pediu reconcilia a partir disso
    public Pagamento cancelar(String idCliente, UUID idTransacao) {
        Pagamento pagamento = consultar(idCliente, idTransacao);

        if (pagamento.statusEm(Instant.now()) == StatusPagamento.PENDENTE) {
            transactionTemplate.execute(status -> repositorio.cancelar(idTransacao));
            pagamento = consultar(idCliente, idTransacao);
        }

        if (pagamento.statusEm(Instant.now()) == StatusPagamento.PAGO) {
            throw new BusinessException("Transação já paga: " + idTransacao);
        }

        return pagamento;
    }

    private Pagamento buscar(UUID idTransacao) {
        return repositorio.findById(idTransacao)
            .orElseThrow(() -> new EntityNotFoundException("Transação não encontrada: " + idTransacao));
    }

    // Sem outbox nem retentativa: webhook perdido fica pro consultar do interno
    private void avisar(Pagamento pagamento, String status) {
        try {
            webhook.avisar(pagamento.getIdCliente(), pagamento.getId(), status);
        } catch (Exception e) {
            log.warn("Webhook {} da transação {} falhou — fica sem aviso", status, pagamento.getId(), e);
        }
    }

}

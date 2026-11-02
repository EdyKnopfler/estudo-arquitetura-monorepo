package com.derso.arquitetura.pagamentointerno;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.derso.arquitetura.pagamentointerno.entity.Pagamento;
import com.derso.arquitetura.pagamentointerno.entity.StatusPagamento;
import com.derso.arquitetura.pagamentointerno.sessaocompra.SessaoCompraClient;
import com.derso.arquitetura.sagas.Messaging;

// Desenho (intenções gravadas antes de qualquer chamada de rede): docs/purchase-flow-design.md#dual-write-no-webhook
@Service
@Profile({ "web", "pendencias" })
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    // StatusPagamento de pagamento-externo (módulo separado, sem tipos compartilhados)
    private static final String STATUS_PAGO_NO_GATEWAY = "PAGO";
    private static final String STATUS_RECUSADO_NO_GATEWAY = "RECUSADO";

    // Bem acima de uma execução (chamada a sessaocompra + publicação): passou disso, quem ocupou caiu
    public static final Duration JANELA_INTENCOES = Duration.ofMinutes(1);

    private final TransactionTemplate transactionTemplate;
    private final PagamentoRepository repositorio;
    private final SessaoCompraClient sessaoCompra;
    private final Messaging sagas;
    private final String filaPagamento;

    public WebhookService(
        PlatformTransactionManager transactionManager,
        PagamentoRepository repositorio,
        SessaoCompraClient sessaoCompra,
        Messaging sagas,
        @Value("${sagas.estafila}") String filaPagamento
    ) throws IOException {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.repositorio = repositorio;
        this.sessaoCompra = sessaoCompra;
        this.sagas = sagas;
        this.filaPagamento = filaPagamento;
        sagas.configurarServico(filaPagamento, Optional.empty(), Optional.empty());
    }

    public void receber(UUID idTransacao, String status) {
        // TODO idTransacao de tentativa anterior (renovada) não acha linha — correlação em docs/to-decide.md
        Pagamento pagamento = repositorio.findByIdExterno(idTransacao)
            .orElseThrow(() -> new IllegalStateException("Webhook sem pagamento correspondente: " + idTransacao));

        switch (status) {
            case STATUS_PAGO_NO_GATEWAY -> pago(pagamento);
            case STATUS_RECUSADO_NO_GATEWAY -> recusado(pagamento);
            default -> throw new IllegalStateException("Status de webhook não tratado para " + pagamento.getId() + ": " + status);
        }
    }

    private void pago(Pagamento pagamento) {
        int linhas = transactionTemplate.execute(status ->
            repositorio.registrarPago(pagamento.getId(), pagamento.getIdExterno(), Instant.now())
        );
        if (linhas == 0) {
            log.info("Webhook pago repetido para {}, ignorado", pagamento.getId());
            return;
        }
        executarIntencoes(pagamento.getId());
    }

    // Sem intenção: perder o aviso só deixa a sessão em EFETUANDO_PAGAMENTO
    private void recusado(Pagamento pagamento) {
        if (pagamento.getStatus() != StatusPagamento.AGUARDANDO_PAGAMENTO) {
            log.info("Webhook recusado para {} em {}, ignorado", pagamento.getId(), pagamento.getStatus());
            return;
        }
        sessaoCompra.pagamentoRecusado(pagamento.getIdSessaoCompra());
    }

    // Chamado pelo job de pendências; executa só se ganhar a ocupação
    public void retomarIntencoes(UUID id) {
        Instant agora = Instant.now();
        int ocupada = transactionTemplate.execute(status ->
            repositorio.ocuparIntencoes(id, agora, agora.minus(JANELA_INTENCOES))
        );
        if (ocupada == 1) {
            executarIntencoes(id);
        }
    }

    // Quem chama já ocupa a linha. Sessão primeiro, mas independentes: falha de uma não impede a outra
    private void executarIntencoes(UUID id) {
        Pagamento pagamento = repositorio.findById(id).orElseThrow();
        try {
            if (pagamento.isSessaoPendente()) {
                try {
                    sessaoCompra.pagamentoEfetuado(pagamento.getIdSessaoCompra());
                    transactionTemplate.execute(status -> repositorio.concluirIntencaoSessao(id));
                } catch (RuntimeException e) {
                    log.warn("Falha ao avisar sessaocompra do pagamento {}, fica pendente", id, e);
                }
            }
            if (pagamento.isFilaPendente()) {
                try {
                    sagas.publicar(filaPagamento, mensagemDaSaga(pagamento));
                    transactionTemplate.execute(status -> repositorio.concluirIntencaoFila(id));
                } catch (IOException | RuntimeException e) {
                    log.warn("Falha ao publicar a SAGA do pagamento {}, fica pendente", id, e);
                }
            }
        } finally {
            transactionTemplate.execute(status -> repositorio.liberarIntencoes(id));
        }
    }

    // Só ids internos — docs/purchase-flow-design.md#payload-da-mensagem-da-saga
    private static Map<String, Object> mensagemDaSaga(Pagamento pagamento) {
        return Map.of(
            "tipo", Messaging.EXECUTE,
            "rastreio", UUID.randomUUID().toString(),
            "idPagamento", pagamento.getId(),
            "idSessaoCompra", pagamento.getIdSessaoCompra(),
            "idReservaHotel", pagamento.getIdReservaHotel(),
            "idReservaVooIda", pagamento.getIdReservaVooIda(),
            "idReservaVooVolta", pagamento.getIdReservaVooVolta()
        );
    }

}

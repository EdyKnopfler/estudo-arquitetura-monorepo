package com.derso.arquitetura.pagamentointerno.pendencias;

import java.time.Instant;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.derso.arquitetura.pagamentointerno.PagamentoRepository;
import com.derso.arquitetura.pagamentointerno.WebhookService;

// Retoma intenções do webhook que falharam ou ficaram com quem caiu — docs/purchase-flow-design.md#dual-write-no-webhook
@Component
@Profile("pendencias")
public class PendenciasTask {

    private static final int TAMANHO_LOTE = 100;

    private final PagamentoRepository repositorio;
    private final WebhookService servico;

    public PendenciasTask(PagamentoRepository repositorio, WebhookService servico) {
        this.repositorio = repositorio;
        this.servico = servico;
    }

    // Um lote por ciclo: o que falhou de novo volta no próximo, sem girar em falso
    @Scheduled(fixedDelayString = "PT10S")
    public void processaPendencias() {
        Instant limite = Instant.now().minus(WebhookService.JANELA_INTENCOES);
        repositorio.comIntencoesLivres(limite, TAMANHO_LOTE).forEach(servico::retomarIntencoes);
    }

}

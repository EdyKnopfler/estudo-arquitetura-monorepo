package com.derso.arquitetura.sessaocompra.timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.derso.arquitetura.sessaocompra.SessaoCompraRepository;
import com.derso.arquitetura.sessaocompra.SessaoCompraService;
import com.derso.arquitetura.sessaocompra.dto.CancelamentoDTO;
import com.derso.arquitetura.sessaocompra.entity.SessaoCompra;

@Component
@Profile("timeout")
public class TimeoutTask {

    private static final int TAMANHO_LOTE = 100;

    private final SessaoCompraRepository repositorio;
    private final SessaoCompraService servico;

    public TimeoutTask(SessaoCompraRepository repositorio, SessaoCompraService servico) {
        this.repositorio = repositorio;
        this.servico = servico;
    }

    // Prazos: docs/purchase-flow-design.md#dois-timeouts
    @Scheduled(fixedDelayString = "PT1M")
    public void processaTimeouts() {
        processa(List.of("INICIADA"), SessaoCompra.PRAZO_INICIADA);
        processa(List.of("CRIANDO_PAGAMENTO", "EFETUANDO_PAGAMENTO"), SessaoCompra.TOLERANCIA_LIMPEZA);
    }

    private void processa(List<String> status, Duration prazo) {
        List<CancelamentoDTO> expiradas;

        do {
            Instant referencia = Instant.now().minus(prazo);
            expiradas = repositorio.marcarLoteComoCancelando(status, TAMANHO_LOTE, referencia);

            expiradas.forEach(servico::desfazerPreReservas);
        } while (expiradas.size() > 0);
    }

}

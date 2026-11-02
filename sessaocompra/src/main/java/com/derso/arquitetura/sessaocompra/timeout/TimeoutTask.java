package com.derso.arquitetura.sessaocompra.timeout;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

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
        // INICIADA tem prazo próprio (reinicia ao voltar pro pedido); o resto conta do início
        processa(() -> repositorio.marcarIniciadasVencidasComoCancelando(TAMANHO_LOTE, Instant.now()));
        processa(() -> repositorio.marcarLoteComoCancelando(
            List.of("CRIANDO_PAGAMENTO", "EFETUANDO_PAGAMENTO", "PAGAMENTO_RECUSADO"),
            TAMANHO_LOTE,
            Instant.now().minus(SessaoCompra.TOLERANCIA_LIMPEZA)
        ));
    }

    private void processa(Supplier<List<CancelamentoDTO>> proximoLote) {
        List<CancelamentoDTO> expiradas;

        do {
            expiradas = proximoLote.get();
            expiradas.forEach(servico::desfazerPreReservas);
        } while (expiradas.size() > 0);
    }

}

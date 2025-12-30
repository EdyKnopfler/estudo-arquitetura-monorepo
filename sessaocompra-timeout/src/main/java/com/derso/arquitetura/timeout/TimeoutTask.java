package com.derso.arquitetura.timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.derso.arquitetura.sessaocompra.SessaoCompraRepository;
import com.derso.arquitetura.sessaocompra.dto.CancelamentoDTO;
import com.derso.arquitetura.sessaocompra.entity.SessaoCompraStatus;

@Component
public class TimeoutTask {

    private static final Duration TEMPO_MAXIMO = Duration.ofMinutes(15);
    private static final int TAMANHO_LOTE = 100;

    private final SessaoCompraRepository repositorio;

    public TimeoutTask(SessaoCompraRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Scheduled(fixedDelayString = "PT1M")
    public void processaTimeouts() {
        List<CancelamentoDTO> expiradas;

        do {
            Instant referencia = Instant.now().minus(TEMPO_MAXIMO);
            expiradas = repositorio.marcarLoteComoCancelando(TAMANHO_LOTE, referencia);
    
            for (CancelamentoDTO sessao : expiradas) {
                try {
                    // TODO chamada aos serviços externos
                    // voo.cancelar(sessao.idReservaVooIda());
                    // hotel.cancelar(sessao.getReservaHotelId());
                    // voo.cancelar(sessao.idReservaVooVolta());
                    repositorio.marcarStatus(sessao.id(), SessaoCompraStatus.CANCELADA);
                } catch (Exception e) {
                    repositorio.marcarStatus(sessao.id(), SessaoCompraStatus.FALHA_CANCELAMENTO);
                }
            }
        } while (expiradas.size() > 0);
    }

}

package com.derso.arquitetura.timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.derso.arquitetura.sessaocompra.app.SessaoCompraRepository;
import com.derso.arquitetura.sessaocompra.entity.SessaoCompra;

@Component
public class TimeoutTask {

    private static final Duration TEMPO_MAXIMO = Duration.ofMinutes(15);
    private static final int TAMANHO_LOTE = 100;

    @Autowired
    private final SessaoCompraRepository repositorio = null;

    @Scheduled(fixedDelayString = "PT1M")
    public void processaTimeouts() {
        Instant referencia = Instant.now().minus(TEMPO_MAXIMO);
        List<SessaoCompra> expiradas = repositorio.marcarLoteComoCancelando(TAMANHO_LOTE, referencia);

        if (expiradas.isEmpty()) {
            return;
        }

        for (SessaoCompra sessao : expiradas) {
            try {
                // TODO chamada aos serviços externos
                // hotel.cancelar(sessao.getReservaHotelId());
                // voo.cancelar(sessao.getReservaVooId());
                repositorio.marcarStatus(sessao.getId(), "CANCELADA");
            } catch (Exception e) {
                repositorio.marcarStatus(sessao.getId(), "FALHA_CANCELAMENTO");
            }
        }
    }

}

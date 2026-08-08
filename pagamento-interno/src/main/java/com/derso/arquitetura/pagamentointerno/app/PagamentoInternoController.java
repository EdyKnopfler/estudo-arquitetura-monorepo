package com.derso.arquitetura.pagamentointerno.app;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.derso.arquitetura.sagas.SagasMessaging;

@RestController
@Profile("web")
public class PagamentoInternoController {

    private static final String FILA_PAGAMENTO = "pagamento";

    private final SagasMessaging sagas;

    public PagamentoInternoController(SagasMessaging sagas) throws IOException {
        this.sagas = sagas;
        sagas.configurarServico(FILA_PAGAMENTO, Optional.empty(), Optional.empty());
    }

    @PostMapping("/webhook")
    public void webhookServicoExterno() throws IOException {
        String rastreio = UUID.randomUUID().toString();

        sagas.publicar(FILA_PAGAMENTO, Map.of(
                "tipo", SagasMessaging.EXECUTE,
                "rastreio", rastreio
        ));

        System.out.println("[webhook] disparando SAGA rastreio=" + rastreio);
    }
}

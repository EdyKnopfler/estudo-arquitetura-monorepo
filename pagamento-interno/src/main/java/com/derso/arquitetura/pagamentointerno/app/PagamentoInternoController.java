package com.derso.arquitetura.pagamentointerno.app;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("web")
public class PagamentoInternoController {

    @PostMapping("/webhook")
    public void webhookServicoExterno() {

    }
}

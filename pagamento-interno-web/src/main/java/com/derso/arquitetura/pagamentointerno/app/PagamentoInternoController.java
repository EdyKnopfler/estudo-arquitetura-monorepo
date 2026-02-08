package com.derso.arquitetura.pagamentointerno.app;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PagamentoInternoController {
    
    @PostMapping("/webhook")
    public void webhookServicoExterno() {
        
    }
}

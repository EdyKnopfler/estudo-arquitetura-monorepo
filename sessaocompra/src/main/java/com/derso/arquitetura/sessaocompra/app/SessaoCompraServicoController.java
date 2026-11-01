package com.derso.arquitetura.sessaocompra.app;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.derso.arquitetura.sessaocompra.SessaoCompraService;

import lombok.RequiredArgsConstructor;

// Chamado por outros serviços (client-id/secret em /servico/**, ver SecurityConfiguration), não pelo
// cliente — por isso sem @PreAuthorize de ownership. Ver docs/purchase-flow-design.md#webhook-do-pagamento.
@RestController
@RequestMapping("/servico/sessoes")
@RequiredArgsConstructor
@Profile("web")
public class SessaoCompraServicoController {

    private final SessaoCompraService service;

    @PutMapping("/{id}/pagamento-efetuado")
    public void pagamentoEfetuado(@PathVariable("id") UUID id) {
        service.pagamentoEfetuado(id);
    }

}

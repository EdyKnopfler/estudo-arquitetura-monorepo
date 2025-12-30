package com.derso.arquitetura.sessaocompra.app;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.derso.arquitetura.sessaocompra.app.dto.CriacaoSessaoRequest;
import com.derso.arquitetura.sessaocompra.app.dto.CriacaoSessaoResponse;
import com.derso.arquitetura.sessaocompra.app.dto.InteracaoDTO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/sessoes")
@RequiredArgsConstructor
public class SessaoCompraController {
    
    private final SessaoCompraService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CriacaoSessaoResponse criar(@RequestBody CriacaoSessaoRequest dados) {
        return new CriacaoSessaoResponse(service.criar(dados.idCliente()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> atualizarEstadoCompra(@PathVariable("id") UUID id, @RequestBody InteracaoDTO novoEstado) {

        // TODO o dado do cliente deve vir com o de autorização (posteriormente)

        if (service.atualizarInteracaoCompra(id, novoEstado)) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/iniciando-pagamento")
    public void iniciarPagamento(@PathVariable("id") UUID id) {

        // TODO clientId para o serviço de Pagamentos

        if (service.iniciarPagamento(id)) {
            // TODO ativar serviço de pagamento
        } else {
            // TODO verificar o cancelamento
        }
    }

    @PutMapping("/{id}/pagamento-efetuado")
    public void pagamentoEfetuado(@PathVariable("id") UUID id) {
        service.pagamentoEfetuado(id);
    }

}

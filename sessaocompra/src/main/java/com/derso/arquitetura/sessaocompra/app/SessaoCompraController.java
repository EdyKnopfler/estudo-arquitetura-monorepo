package com.derso.arquitetura.sessaocompra.app;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.derso.arquitetura.sessaocompra.SessaoCompraService;
import com.derso.arquitetura.sessaocompra.app.dto.CriacaoSessaoResponse;
import com.derso.arquitetura.sessaocompra.dto.InteracaoDTO;
import com.derso.arquitetura.webbase.jwt.UsuarioAutenticado;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/sessoes")
@RequiredArgsConstructor
@Profile("web")
public class SessaoCompraController {

    private final SessaoCompraService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CriacaoSessaoResponse criar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return new CriacaoSessaoResponse(service.criar(UUID.fromString(usuario.id())));
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
        // Ainda decidindo quem chama quem, a ideia é de que este serviço não fique agarrado coordenando.
        // Deve somente ser chamado para arbitrar.

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

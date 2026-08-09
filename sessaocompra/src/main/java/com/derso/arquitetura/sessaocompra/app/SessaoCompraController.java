package com.derso.arquitetura.sessaocompra.app;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.derso.arquitetura.sessaocompra.SessaoCompraService;
import com.derso.arquitetura.sessaocompra.app.dto.CriacaoSessaoResponse;
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

    @PutMapping("/{id}/hotel")
    @PreAuthorize("@sessaoOwnership.pertence(#id, authentication)")
    public void definirHotel(@PathVariable("id") UUID id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.definirHotel(id, UUID.fromString(usuario.id()));
    }

    @PutMapping("/{id}/voo-ida")
    @PreAuthorize("@sessaoOwnership.pertence(#id, authentication)")
    public void definirVooIda(@PathVariable("id") UUID id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.definirVooIda(id, UUID.fromString(usuario.id()));
    }

    @PutMapping("/{id}/voo-volta")
    @PreAuthorize("@sessaoOwnership.pertence(#id, authentication)")
    public void definirVooVolta(@PathVariable("id") UUID id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.definirVooVolta(id, UUID.fromString(usuario.id()));
    }

    @PutMapping("/{id}/iniciando-pagamento")
    @PreAuthorize("@sessaoOwnership.pertence(#id, authentication)")
    public void iniciarPagamento(@PathVariable("id") UUID id) {

        // TODO clientId para o serviço de Pagamentos
        // Ainda decidindo quem chama quem, a ideia é de que este serviço não fique agarrado coordenando.
        // Deve somente ser chamado para arbitrar.

        service.iniciarPagamento(id);

        // TODO ativar serviço de pagamento
    }

    // Sem @PreAuthorize de propósito: este endpoint é candidato a virar consumidor de fila SAGA
    // (ver docs/purchase-flow-design.md), não uma chamada de cliente via JWT — fora do escopo desta rodada.
    @PutMapping("/{id}/pagamento-efetuado")
    public void pagamentoEfetuado(@PathVariable("id") UUID id) {
        service.pagamentoEfetuado(id);
    }

}

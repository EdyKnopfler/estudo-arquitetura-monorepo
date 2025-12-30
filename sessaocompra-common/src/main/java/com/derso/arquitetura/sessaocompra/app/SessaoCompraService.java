package com.derso.arquitetura.sessaocompra.app;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.derso.arquitetura.sessaocompra.app.dto.InteracaoDTO;
import com.derso.arquitetura.sessaocompra.entity.SessaoCompra;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessaoCompraService {

    private final SessaoCompraRepository repositorio;
    
    @Transactional
    public UUID criar(UUID idCliente) {
        SessaoCompra novaSessao = new SessaoCompra(idCliente);
        repositorio.save(novaSessao);
        return novaSessao.getId();
    }

    // JOGAMOS OS UPDATES PARA O BANCO PARA EVITAR CONDIÇÕES DE CORRIDA

    @Transactional
    public boolean atualizarInteracaoCompra(UUID uuid, InteracaoDTO novoEstado) {
        return repositorio.updateSessaoInteracaoCompra(
            uuid, 
            novoEstado.idCliente(),
            novoEstado.idReservaVooIda(),
            novoEstado.idReservaHotel(),
            novoEstado.idReservaVooVolta()
        ) > 0;
    }

    @Transactional
    public boolean iniciarPagamento(UUID id) {
        return repositorio.iniciarPagamento(id) > 0;
    }

    @Transactional
    public void pagamentoEfetuado(UUID id) {
        repositorio.pagamentoEfetuado(id);
    }

}

package com.derso.arquitetura.sessaocompra.app;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.derso.arquitetura.sessaocompra.app.SessaoCompraRepository.ExpiradoDTO;
import com.derso.arquitetura.sessaocompra.app.dto.InteracaoDTO;
import com.derso.arquitetura.sessaocompra.entity.SessaoCompra;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessaoCompraService {

    private static final Duration TEMPO_MAXIMO = Duration.ofMinutes(15);

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
    public void processarExpirados() {
        Instant referencia = Instant.now().minus(TEMPO_MAXIMO);

        
        // TODO depois com broker e outros serviços, disparar o SAGAS de cancelamento das pré-reservas. 
        // Possivelmente só o update em massa não funcionará, é preciso obter os ids das reservas (SELECT FOR UPDATE)
        // e sair enviando as mensagens.
        // SELECT FOR UPDATE => não concorrer com o método iniciarPagamento

        List<ExpiradoDTO> expirados = repositorio.cancelarExpirados(referencia);
        // System.out.println(expirados);
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

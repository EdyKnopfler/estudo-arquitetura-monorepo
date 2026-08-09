package com.derso.arquitetura.sessaocompra;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.derso.arquitetura.sessaocompra.entity.SessaoCompra;
import com.derso.arquitetura.sessaocompra.entity.SessaoCompraStatus;
import com.derso.arquitetura.sessaocompra.reservasinterno.ReservasInternoHotelClient;
import com.derso.arquitetura.sessaocompra.reservasinterno.ReservasInternoVooClient;
import com.derso.arquitetura.webbase.config.BusinessException;

import jakarta.persistence.EntityNotFoundException;

@Service
public class SessaoCompraService {

    private final SessaoCompraRepository repositorio;
    private final ReservasInternoHotelClient reservasInternoHotelClient;
    private final ReservasInternoVooClient reservasInternoVooClient;
    private final TransactionTemplate transactionTemplate;

    public SessaoCompraService(
        SessaoCompraRepository repositorio,
        ReservasInternoHotelClient reservasInternoHotelClient,
        ReservasInternoVooClient reservasInternoVooClient,
        PlatformTransactionManager transactionManager
    ) {
        this.repositorio = repositorio;
        this.reservasInternoHotelClient = reservasInternoHotelClient;
        this.reservasInternoVooClient = reservasInternoVooClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public UUID criar(UUID idCliente) {
        SessaoCompra novaSessao = new SessaoCompra(idCliente);
        repositorio.save(novaSessao);
        return novaSessao.getId();
    }

    // Nunca chamar reservas-interno dentro de transação (convenção de ReservasService).
    // Ver docs/purchase-flow-design.md.

    public void definirHotel(UUID id, UUID idCliente) {
        SessaoCompra sessao = buscarSessaoParaEscolha(id);
        validarSessaoAtiva(sessao);

        UUID idReservaAtual = sessao.getIdReservaHotel();
        UUID idReserva = idReservaAtual == null
            ? reservasInternoHotelClient.criar(idCliente)
            : reservasInternoHotelClient.trocar(idReservaAtual, idCliente);

        int linhas = transactionTemplate.execute(status -> repositorio.atualizarHotel(id, idReserva));
        if (linhas == 0) {
            throw new BusinessException("Sessão de compra não aceita mais alterações");
        }
    }

    public void definirVooIda(UUID id, UUID idCliente) {
        SessaoCompra sessao = buscarSessaoParaEscolha(id);
        validarSessaoAtiva(sessao);

        UUID idReservaAtual = sessao.getIdReservaVooIda();
        UUID idReserva = idReservaAtual == null
            ? reservasInternoVooClient.criar(idCliente)
            : reservasInternoVooClient.trocar(idReservaAtual, idCliente);

        int linhas = transactionTemplate.execute(status -> repositorio.atualizarVooIda(id, idReserva));
        if (linhas == 0) {
            throw new BusinessException("Sessão de compra não aceita mais alterações");
        }
    }

    public void definirVooVolta(UUID id, UUID idCliente) {
        SessaoCompra sessao = buscarSessaoParaEscolha(id);
        validarSessaoAtiva(sessao);

        UUID idReservaAtual = sessao.getIdReservaVooVolta();
        UUID idReserva = idReservaAtual == null
            ? reservasInternoVooClient.criar(idCliente)
            : reservasInternoVooClient.trocar(idReservaAtual, idCliente);

        int linhas = transactionTemplate.execute(status -> repositorio.atualizarVooVolta(id, idReserva));
        if (linhas == 0) {
            throw new BusinessException("Sessão de compra não aceita mais alterações");
        }
    }

    private SessaoCompra buscarSessaoParaEscolha(UUID id) {
        return repositorio.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Sessão de compra não encontrada: " + id));
    }

    private void validarSessaoAtiva(SessaoCompra sessao) {
        if (sessao.getStatus() != SessaoCompraStatus.INICIADA) {
            throw new BusinessException("Sessão de compra não está mais aceitando alterações: " + sessao.getStatus());
        }
    }

    @Transactional
    public void iniciarPagamento(UUID id) {
        if (repositorio.iniciarPagamento(id) > 0) {
            return;
        }

        SessaoCompra sessao = repositorio.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Sessão de compra não encontrada: " + id));

        if (sessao.getStatus() != SessaoCompraStatus.INICIADA) {
            throw new BusinessException("Sessão de compra não está mais aceitando alterações: " + sessao.getStatus());
        }

        throw new BusinessException("Sessão de compra incompleta: faltam reservas de hotel e/ou voo");
    }

    @Transactional
    public void pagamentoEfetuado(UUID id) {
        repositorio.pagamentoEfetuado(id);
    }

}

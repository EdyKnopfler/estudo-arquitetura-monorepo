package com.derso.arquitetura.sessaocompra;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.derso.arquitetura.sessaocompra.entity.SessaoCompra;
import com.derso.arquitetura.sessaocompra.entity.SessaoCompraStatus;
import com.derso.arquitetura.sessaocompra.pagamentointerno.PagamentoInternoClient;
import com.derso.arquitetura.sessaocompra.reservasinterno.ReservasInternoHotelClient;
import com.derso.arquitetura.sessaocompra.reservasinterno.ReservasInternoVooClient;
import com.derso.arquitetura.webbase.config.BusinessException;

import jakarta.persistence.EntityNotFoundException;

@Service
public class SessaoCompraService {

    private static final Logger log = LoggerFactory.getLogger(SessaoCompraService.class);

    private final SessaoCompraRepository repositorio;
    private final ReservasInternoHotelClient reservasInternoHotelClient;
    private final ReservasInternoVooClient reservasInternoVooClient;
    private final PagamentoInternoClient pagamentoInternoClient;
    private final TransactionTemplate transactionTemplate;

    public SessaoCompraService(
        SessaoCompraRepository repositorio,
        ReservasInternoHotelClient reservasInternoHotelClient,
        ReservasInternoVooClient reservasInternoVooClient,
        PagamentoInternoClient pagamentoInternoClient,
        PlatformTransactionManager transactionManager
    ) {
        this.repositorio = repositorio;
        this.reservasInternoHotelClient = reservasInternoHotelClient;
        this.reservasInternoVooClient = reservasInternoVooClient;
        this.pagamentoInternoClient = pagamentoInternoClient;
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

    // Nunca chamar pagamento-interno dentro de transação (mesma convenção de reservas-interno acima).
    // Sem outbox aqui: a linha em `pagamentos` só é salva depois do `/efetuar` responder (ver
    // PagamentoService.criarPagamento), então uma falha nesta chamada não deixa nenhum efeito
    // colateral em pagamento-interno pra compensar — só reverte o próprio status local e devolve
    // a falha pro front-end tentar de novo.
    public void iniciarPagamento(UUID id) {
        int linhas = transactionTemplate.execute(status -> repositorio.iniciarPagamento(id));
        if (linhas == 0) {
            SessaoCompra sessao = repositorio.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Sessão de compra não encontrada: " + id));

            if (sessao.getStatus() != SessaoCompraStatus.INICIADA) {
                throw new BusinessException("Sessão de compra não está mais aceitando alterações: " + sessao.getStatus());
            }

            throw new BusinessException("Sessão de compra incompleta: faltam reservas de hotel e/ou voo");
        }

        SessaoCompra sessao = repositorio.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Sessão de compra não encontrada: " + id));

        try {
            pagamentoInternoClient.criar(id, sessao.getIdReservaHotel(), sessao.getIdReservaVooIda(), sessao.getIdReservaVooVolta());
        } catch (Exception e) {
            log.warn("Falha ao iniciar pagamento para sessão {}, revertendo status", id, e);
            transactionTemplate.execute(status -> repositorio.reverterPagamento(id));
            throw new BusinessException("Falha ao iniciar pagamento, tente novamente");
        }

        transactionTemplate.execute(status -> repositorio.pagamentoCriado(id));
    }

    @Transactional
    public void pagamentoEfetuado(UUID id) {
        repositorio.pagamentoEfetuado(id);
    }

}

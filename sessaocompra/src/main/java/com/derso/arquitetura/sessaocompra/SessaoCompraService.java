package com.derso.arquitetura.sessaocompra;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.derso.arquitetura.sessaocompra.app.dto.EtapaCompra;
import com.derso.arquitetura.sessaocompra.app.dto.IniciarPagamentoResponse;
import com.derso.arquitetura.sessaocompra.dto.CancelamentoDTO;
import com.derso.arquitetura.sessaocompra.entity.SessaoCompra;
import com.derso.arquitetura.sessaocompra.entity.SessaoCompraStatus;
import com.derso.arquitetura.sessaocompra.pagamentointerno.PagamentoInternoClient;
import com.derso.arquitetura.sessaocompra.pagamentointerno.dto.PagamentoInternoResponse;
import com.derso.arquitetura.sessaocompra.reservasinterno.ReservasInternoHotelClient;
import com.derso.arquitetura.sessaocompra.reservasinterno.ReservasInternoVooClient;
import com.derso.arquitetura.webbase.config.BusinessException;

import jakarta.persistence.EntityNotFoundException;

@Service
public class SessaoCompraService {

    private static final Logger log = LoggerFactory.getLogger(SessaoCompraService.class);

    // StatusPagamento de pagamento-interno (módulo separado, sem tipos compartilhados)
    private static final String STATUS_AGUARDANDO_PAGAMENTO = "AGUARDANDO_PAGAMENTO";
    private static final String STATUS_PAGO = "PAGO";
    private static final String STATUS_CRIANDO = "CRIANDO";

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
    // Falha não reverte a sessão, e já iniciado repete o PUT (pagamento-interno guarda a URL) —
    // ver docs/purchase-flow-design.md#cadeia-idempotente.
    public IniciarPagamentoResponse iniciarPagamento(UUID id) {
        int linhas = transactionTemplate.execute(status -> repositorio.iniciarPagamento(id));
        SessaoCompra sessao = repositorio.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Sessão de compra não encontrada: " + id));

        if (linhas == 0) {
            switch (sessao.getStatus()) {
                case CRIANDO_PAGAMENTO, EFETUANDO_PAGAMENTO -> { }
                case INICIADA -> throw new BusinessException("Sessão de compra incompleta: faltam reservas de hotel e/ou voo");
                default -> throw new BusinessException("Sessão de compra não está mais aceitando alterações: " + sessao.getStatus());
            }
        }

        PagamentoInternoResponse pagamento;
        try {
            pagamento = pagamentoInternoClient.criar(
                id, sessao.getIdReservaHotel(), sessao.getIdReservaVooIda(), sessao.getIdReservaVooVolta()
            );
        } catch (Exception e) {
            log.warn("Falha ao criar pagamento para sessão {}, status mantido", id, e);
            throw new BusinessException("Falha ao iniciar pagamento, tente novamente");
        }

        // Só responde: o status da sessão fecha pelo fluxo do webhook, não pela retentativa
        if (STATUS_PAGO.equals(pagamento.status())) {
            return new IniciarPagamentoResponse(EtapaCompra.JA_PAGO, null, null);
        }

        // Outra chamada ainda em voo no pagamento-interno; a sessão segue em CRIANDO_PAGAMENTO
        if (STATUS_CRIANDO.equals(pagamento.status())) {
            return new IniciarPagamentoResponse(EtapaCompra.AGUARDAR, null, null);
        }

        // Só o caminho feliz; outro estado falha alto até ganhar tratamento — docs/state-driven-endpoints.md
        if (!STATUS_AGUARDANDO_PAGAMENTO.equals(pagamento.status())) {
            throw new IllegalStateException("Estado de pagamento não tratado para sessão " + id + ": " + pagamento.status());
        }

        transactionTemplate.execute(status -> repositorio.pagamentoCriado(id));
        return new IniciarPagamentoResponse(EtapaCompra.PAGAR, pagamento.urlPagamento(), pagamento.vencimento());
    }

    // Chamado pelo TimeoutTask, com a sessão já marcada CANCELANDO. Prazo é só do job: a requisição
    // só olha o status — docs/state-driven-endpoints.md. Falha ao desfazer fica em FALHA_CANCELAMENTO.
    public void desfazerPreReservas(CancelamentoDTO sessao) {
        try {
            if (sessao.idReservaHotel() != null) {
                reservasInternoHotelClient.cancelar(sessao.idReservaHotel());
            }
            if (sessao.idReservaVooIda() != null) {
                reservasInternoVooClient.cancelar(sessao.idReservaVooIda());
            }
            if (sessao.idReservaVooVolta() != null) {
                reservasInternoVooClient.cancelar(sessao.idReservaVooVolta());
            }
            repositorio.marcarStatusCancelamento(sessao.id(), SessaoCompraStatus.CANCELADA);
        } catch (Exception e) {
            log.warn("Falha ao desfazer pré-reservas da sessão expirada {}", sessao.id(), e);
            repositorio.marcarStatusCancelamento(sessao.id(), SessaoCompraStatus.FALHA_CANCELAMENTO);
        }
    }

    @Transactional
    public void pagamentoEfetuado(UUID id) {
        repositorio.pagamentoEfetuado(id);
    }

}

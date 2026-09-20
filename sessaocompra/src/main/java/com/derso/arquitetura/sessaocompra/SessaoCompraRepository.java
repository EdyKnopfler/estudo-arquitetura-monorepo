package com.derso.arquitetura.sessaocompra;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.derso.arquitetura.sessaocompra.dto.CancelamentoDTO;
import com.derso.arquitetura.sessaocompra.entity.SessaoCompra;
import com.derso.arquitetura.sessaocompra.entity.SessaoCompraStatus;

public interface SessaoCompraRepository extends JpaRepository<SessaoCompra, UUID> {

    boolean existsByIdAndIdCustomer(UUID id, UUID idCustomer);

    @Modifying
    @Query("""
        UPDATE SessaoCompra s
        SET s.idReservaHotel = :idReservaHotel
        WHERE s.id = :idSessao
            AND s.status = 'INICIADA'
    """)
    int atualizarHotel(@Param("idSessao") UUID idSessao, @Param("idReservaHotel") UUID idReservaHotel);

    @Modifying
    @Query("""
        UPDATE SessaoCompra s
        SET s.idReservaVooIda = :idReservaVooIda
        WHERE s.id = :idSessao
            AND s.status = 'INICIADA'
    """)
    int atualizarVooIda(@Param("idSessao") UUID idSessao, @Param("idReservaVooIda") UUID idReservaVooIda);

    @Modifying
    @Query("""
        UPDATE SessaoCompra s
        SET s.idReservaVooVolta = :idReservaVooVolta
        WHERE s.id = :idSessao
            AND s.status = 'INICIADA'
    """)
    int atualizarVooVolta(@Param("idSessao") UUID idSessao, @Param("idReservaVooVolta") UUID idReservaVooVolta);

    // CRIANDO_PAGAMENTO = fase síncrona (esta chamada + PagamentoInternoClient.criar), curta e sob
    // nosso controle — travar aqui por mais que alguns segundos é bug/infra, nunca demora legítima
    // do usuário. Só vira EFETUANDO_PAGAMENTO depois do /efetuar responder (pagamentoCriado abaixo),
    // que é quando a espera passa a ser pelo usuário/webhook — ver docs/purchase-flow-design.md.
    @Modifying
    @Query("""
        UPDATE SessaoCompra s
        SET
            s.status = 'CRIANDO_PAGAMENTO'
        WHERE s.id = :idSessao
            AND s.status = 'INICIADA'
            AND s.idReservaVooIda IS NOT NULL
            AND s.idReservaHotel IS NOT NULL
            AND s.idReservaVooVolta IS NOT NULL
    """)
    int iniciarPagamento(@Param("idSessao") UUID id);

    // Reversão da transição acima — chamada `pagamento-interno` falhou antes de qualquer efeito
    // colateral existir (linha em `pagamentos` só é salva depois do `/efetuar` responder, ver
    // PagamentoService.criarPagamento) — não é dual-write, é só desfazer o próprio status local.
    @Modifying
    @Query("""
        UPDATE SessaoCompra s
        SET
            s.status = 'INICIADA'
        WHERE s.id = :idSessao
            AND s.status = 'CRIANDO_PAGAMENTO'
    """)
    int reverterPagamento(@Param("idSessao") UUID id);

    // pagamento-interno confirmou a criação (id_externo já existe do lado de lá) — a partir daqui
    // a espera é pelo usuário interagir no serviço externo + retorno do webhook, não mais por nós.
    @Modifying
    @Query("""
        UPDATE SessaoCompra s
        SET
            s.status = 'EFETUANDO_PAGAMENTO'
        WHERE s.id = :idSessao
            AND s.status = 'CRIANDO_PAGAMENTO'
    """)
    int pagamentoCriado(@Param("idSessao") UUID id);

    @Modifying
    @Query("""
        UPDATE SessaoCompra s
        SET
            s.status = 'PAGAMENTO_EFETUADO'
        WHERE s.id = :idSessao
            AND s.status = 'EFETUANDO_PAGAMENTO'
    """)
    void pagamentoEfetuado(@Param("idSessao") UUID id);

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            UPDATE sessao_compra
            SET status = 'CANCELANDO'
            WHERE id IN (
                SELECT id
                FROM sessao_compra
                WHERE status = 'INICIADA'
                AND start_time < :horaRef
                ORDER BY start_time
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            RETURNING
                id,
                id_reserva_voo_ida AS idReservaVooIda,
                id_reserva_hotel AS idReservaHotel,
                id_reserva_voo_volta AS idReservaVooVolta;
        """
    )
    List<CancelamentoDTO> marcarLoteComoCancelando(@Param("batchSize") int tamanhoLote, @Param("horaRef") Instant horaRef);

    @Modifying
    @Transactional
    @Query("""
        UPDATE SessaoCompra s
        SET s.status = :novoStatus
        WHERE s.id = :idSessao AND s.status = 'CANCELANDO'
    """)
    void marcarStatusCancelamento(@Param("idSessao") UUID id, @Param("novoStatus") SessaoCompraStatus novoStatus);

}

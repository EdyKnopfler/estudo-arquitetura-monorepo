package com.derso.arquitetura.sessaocompra.app;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.derso.arquitetura.sessaocompra.entity.SessaoCompra;

public interface SessaoCompraRepository extends JpaRepository<SessaoCompra, UUID> {
    
    @Modifying
    @Query("""
        UPDATE SessaoCompra s 
        SET 
            s.idCustomer = :idCustomer,
            s.idReservaVooIda = :idReservaVooIda,
            s.idReservaHotel = :idReservaHotel,
            s.idReservaVooVolta = :idReservaVooVolta
        WHERE s.id = :idSessao 
            AND (s.idCustomer IS NULL OR s.idCustomer = :idCustomer)
            AND s.status = 'INICIADA'
    """)
    int updateSessaoInteracaoCompra(
        @Param("idSessao") UUID idSessao,
        @Param("idCustomer") UUID idCustomer, 
        @Param("idReservaVooIda") UUID idReservaVooIda, 
        @Param("idReservaHotel") UUID idReservaHotel, 
        @Param("idReservaVooVolta") UUID idReservaVooVolta
    );

    @Modifying
    @Query("""
        UPDATE SessaoCompra s
        SET
            s.status = 'EFETUANDO_PAGAMENTO'
        WHERE s.id = :idSessao
            AND s.status = 'INICIADA'
    """)
    int iniciarPagamento(@Param("idSessao") UUID id);

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
                AND inicio < :horaRef
                ORDER BY inicio
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
    List<SessaoCompra> marcarLoteComoCancelando(@Param("batchSize") int tamanhoLote, @Param("horaRef") Instant horaRef);

    @Modifying
    @Transactional
    @Query("""
        UPDATE SessaoDTO s
        SET s.status = :novoStatus
        WHERE s.id = :idSessao
    """)
    void marcarStatus(@Param("idSessao") UUID id, @Param("novoStatus") String novoStatus);

}

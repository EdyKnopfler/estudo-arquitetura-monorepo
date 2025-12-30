package com.derso.arquitetura.sessaocompra.app;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // TODO mais tarde com SELECT FOR UPDATE pois deveremos pegar os ids de reservas para cancelar nos outros serviços

    public static interface ExpiradoDTO {
        public UUID getIdSessao();
        public UUID getIdCustomer();
        public UUID getIdReservaVooIda();
        public UUID getIdReservaHotel();
        public UUID getIdReservaVooVolta();
    }

    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            UPDATE sessao_compra
            SET
                status = 'CANCELADA'
            WHERE start_time < :horaRef
                AND status = 'INICIADA'
            RETURNING
                id AS idSessao, id_customer AS idCustomer,
                id_reserva_voo_ida AS idReservaVooIda, id_reserva_hotel AS idReservaHotel, id_reserva_voo_volta as idReservaVooVolta
        """
    )
    List<ExpiradoDTO> cancelarExpirados(@Param("horaRef") Instant horaRef);

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

}

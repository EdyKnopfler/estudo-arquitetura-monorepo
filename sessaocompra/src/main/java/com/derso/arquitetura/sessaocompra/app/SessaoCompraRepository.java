package com.derso.arquitetura.sessaocompra.app;

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

}

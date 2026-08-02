package com.derso.arquitetura.sessaocompra.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessao_compra")
@Getter
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class SessaoCompra {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessaoCompraStatus status;

    @Column(name = "id_customer")
    private UUID idCustomer;

    @Column(name = "id_reserva_voo_ida")
    private UUID idReservaVooIda;

    @Column(name = "id_reserva_hotel")
    private UUID idReservaHotel;

    @Column(name = "id_reserva_voo_volta")
    private UUID idReservaVooVolta;

    @Column(name = "start_time")
    private Instant inicio;

    public SessaoCompra(UUID idCustomer) {
        this.id = UUID.randomUUID();
        this.idCustomer = idCustomer;
        this.status = SessaoCompraStatus.INICIADA;
        this.inicio = Instant.now();
    }

}

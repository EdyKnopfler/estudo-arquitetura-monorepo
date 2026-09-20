package com.derso.arquitetura.pagamentointerno.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pagamentos")
@Getter
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class Pagamento {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "id_externo", nullable = false, updatable = false)
    private UUID idExterno;

    @Column(name = "id_sessao_compra", nullable = false, updatable = false)
    private UUID idSessaoCompra;

    @Column(name = "id_reserva_hotel", nullable = false, updatable = false)
    private UUID idReservaHotel;

    @Column(name = "id_reserva_voo_ida", nullable = false, updatable = false)
    private UUID idReservaVooIda;

    @Column(name = "id_reserva_voo_volta", nullable = false, updatable = false)
    private UUID idReservaVooVolta;

    public Pagamento(UUID idExterno, UUID idSessaoCompra, UUID idReservaHotel, UUID idReservaVooIda, UUID idReservaVooVolta) {
        this.id = UUID.randomUUID();
        this.idExterno = idExterno;
        this.idSessaoCompra = idSessaoCompra;
        this.idReservaHotel = idReservaHotel;
        this.idReservaVooIda = idReservaVooIda;
        this.idReservaVooVolta = idReservaVooVolta;
    }

}

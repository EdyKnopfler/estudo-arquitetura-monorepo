package com.derso.arquitetura.sessaocompra.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

import com.derso.arquitetura.sessaocompra.config.SessaoCompraException;

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

    public SessaoCompra(UUID idCustomer) {
        this.id = UUID.randomUUID();
        this.idCustomer = idCustomer;
        this.status = SessaoCompraStatus.INICIADA;
    }

    public void anexarCliente(UUID idCustomer) {
        exigirNaoFinalizada();
        this.idCustomer = idCustomer;
    }

    public void anexarReservaVooIda(UUID reservaId) {
        exigirStatus(SessaoCompraStatus.INICIADA);
        this.idReservaVooIda = reservaId;
    }

    public void anexarReservaHotel(UUID reservaId) {
        exigirStatus(SessaoCompraStatus.INICIADA);
        this.idReservaHotel = reservaId;
    }

    public void anexarReservaVooVolta(UUID reservaId) {
        exigirStatus(SessaoCompraStatus.INICIADA);
        this.idReservaVooVolta = reservaId;
    }

    public void iniciarPagamento() {
        exigirStatus(SessaoCompraStatus.INICIADA);
        exigirCliente();
        this.status = SessaoCompraStatus.EFETUANDO_PAGAMENTO;
    }

    public void confirmarPagamento() {
        exigirStatus(SessaoCompraStatus.EFETUANDO_PAGAMENTO);
        this.status = SessaoCompraStatus.PAGAMENTO_EFETUADO;
    }

    public void confirmarViagemReservada() {
        exigirStatus(SessaoCompraStatus.PAGAMENTO_EFETUADO);
        this.status = SessaoCompraStatus.VIAGEM_RESERVADA;
    }

    public void cancelar() {
        exigirNaoFinalizada();
        this.status = SessaoCompraStatus.CANCELADA;
    }

    public void marcarErro() {
        this.status = SessaoCompraStatus.ERRO;
    }

    private void exigirCliente() {
        if (idCustomer == null) {
            throw new SessaoCompraException("Cliente é obrigatório para finalizar a compra");
        }
    }

    private void exigirNaoFinalizada() {
        if (status.finalizada()) {
            throw new SessaoCompraException("Sessão já finalizada: " + status);
        }
    }

    private void exigirStatus(SessaoCompraStatus esperado) {
        if (this.status != esperado) {
            throw new SessaoCompraException(
                "Transição inválida. Status atual: " + status + ", esperado: " + esperado);
        }
    }
}

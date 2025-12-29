package com.derso.arquitetura.sessaocompra.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
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

    @Column(name = "start_time")
    private Instant inicio;

    public SessaoCompra(UUID idCustomer) {
        this.id = UUID.randomUUID();
        this.idCustomer = idCustomer;
        this.status = SessaoCompraStatus.INICIADA;
        this.inicio = Instant.now();
    }

    // TODO refletindo se mantenho estes métodos ou deixo tudo com os UPDATES
    // Se um update não encontrar a linha no estado válido, preciso buscá-la para verificar o que ocorreu
    // Talvez fazendo com JAVA, sempre começando com um select com lock pessimista, fique mais amigável (embora menos performático)
    // Quando decidir eu resolvo hahaha. ChatGPT to the rescue.

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

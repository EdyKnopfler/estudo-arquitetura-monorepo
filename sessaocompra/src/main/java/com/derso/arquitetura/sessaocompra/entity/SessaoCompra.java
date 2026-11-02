package com.derso.arquitetura.sessaocompra.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessao_compra")
@Getter
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class SessaoCompra {

    // Prazo pra escolher e avançar, em INICIADA — docs/purchase-flow-design.md#dois-timeouts
    public static final Duration PRAZO_INICIADA = Duration.ofMinutes(15);

    // Limpeza de sessão largada com pagamento em criação/criado; o pagamento em si é do timeout do pagamento
    public static final Duration TOLERANCIA_LIMPEZA = Duration.ofHours(1);

    // Prazo documentado da pré-reserva no -externo (TEMPO_MAXIMO lá), contado desde o início — nada estende além
    public static final Duration TETO = Duration.ofHours(1);

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

    @Column(name = "prazo_iniciada")
    private Instant prazoIniciada;

    public SessaoCompra(UUID idCustomer) {
        this.id = UUID.randomUUID();
        this.idCustomer = idCustomer;
        this.status = SessaoCompraStatus.INICIADA;
        this.inicio = Instant.now();
        this.prazoIniciada = inicio.plus(PRAZO_INICIADA);
    }

    // Voltar pra INICIADA reinicia o prazo, limitado ao teto
    public Instant prazoAoVoltarPraIniciada(Instant agora) {
        Instant reiniciado = agora.plus(PRAZO_INICIADA);
        Instant teto = inicio.plus(TETO);
        return reiniciado.isBefore(teto) ? reiniciado : teto;
    }

}

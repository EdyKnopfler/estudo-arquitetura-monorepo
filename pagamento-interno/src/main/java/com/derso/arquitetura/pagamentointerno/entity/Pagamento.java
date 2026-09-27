package com.derso.arquitetura.pagamentointerno.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Criada e alterada só via PagamentoRepository (insert/updates condicionais).
@Entity
@Table(name = "pagamentos")
@Getter
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class Pagamento {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "id_sessao_compra", nullable = false, updatable = false)
    private UUID idSessaoCompra;

    @Column(name = "id_reserva_hotel", nullable = false, updatable = false)
    private UUID idReservaHotel;

    @Column(name = "id_reserva_voo_ida", nullable = false, updatable = false)
    private UUID idReservaVooIda;

    @Column(name = "id_reserva_voo_volta", nullable = false, updatable = false)
    private UUID idReservaVooVolta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPagamento status;

    @Column(name = "chave_idempotencia", nullable = false)
    private UUID chaveIdempotencia;

    @Column(name = "id_externo")
    private UUID idExterno;

    @Column(name = "url_pagamento")
    private String urlPagamento;

}

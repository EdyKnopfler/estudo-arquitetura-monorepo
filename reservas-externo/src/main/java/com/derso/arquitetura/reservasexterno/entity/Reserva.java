package com.derso.arquitetura.reservasexterno.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reservas")
@Getter
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class Reserva {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "id_cliente")
    private UUID idCliente;

    @Column(name = "criacao")
    private Instant criacao;

    @Column(name = "confirmado")
    private boolean confirmado;

    public Reserva(UUID idCliente) {
        this.id = UUID.randomUUID();
        this.idCliente = idCliente;
        this.criacao = Instant.now();
        this.confirmado = false;
    }
    
}

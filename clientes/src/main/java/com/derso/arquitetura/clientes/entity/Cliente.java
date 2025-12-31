package com.derso.arquitetura.clientes.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
    name = "clientes",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_cliente_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_cliente_cpf", columnNames = "cpf")
    }
)
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@AllArgsConstructor
@Getter
public class Cliente {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 11)
    private String cpf;

    @Column(nullable = false, length = 60)
    private String senha;

}

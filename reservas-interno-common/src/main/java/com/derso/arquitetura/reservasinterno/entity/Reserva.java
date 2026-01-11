package com.derso.arquitetura.reservasinterno.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * Simplificação do controle "interno" de reservas. Consideramos que há somente um serviço externo.
 * Em um caso real poderíamos ter um atributo indicando qual o serviço externo de hotel ou voo é acessado para
 * confirmar a viagem, conforme os fornecedores reais envolvidos.
 */
@Entity
@Table(name = "reservas")
@Getter
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class Reserva {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "id_externo")
    private UUID idExterno;

    public Reserva(UUID idExterno) {
        this.id = UUID.randomUUID();
        this.idExterno = idExterno;
    }
    
}

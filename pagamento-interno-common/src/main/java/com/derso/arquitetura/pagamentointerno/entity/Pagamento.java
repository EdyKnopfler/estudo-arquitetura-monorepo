package com.derso.arquitetura.pagamentointerno.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "pagamentos")
@Getter
public class Pagamento {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;
    
}

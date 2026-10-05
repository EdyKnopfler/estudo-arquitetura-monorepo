package com.derso.arquitetura.pagamentoexterno.entity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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

// Criada só via PagamentoRepository (insert idempotente).
@Entity
@Table(name = "pagamentos")
@Getter
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class Pagamento {

    // Expira por relógio, sem job nem coluna — docs/purchase-flow-design.md#premissas-do-gateway-simulado-pagamento-externo
    public static final Duration PRAZO = Duration.ofMinutes(15);

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    // client-id de quem criou — pagar não autentica, então o webhook é roteado por aqui
    @Column(name = "id_cliente", nullable = false, updatable = false)
    private String idCliente;

    @Column(name = "chave_idempotencia", nullable = false, updatable = false)
    private UUID chaveIdempotencia;

    @Column(nullable = false, updatable = false)
    private String metodo;

    @Column(nullable = false, updatable = false)
    private BigDecimal valor;

    @Column(nullable = false, updatable = false)
    private Instant criacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPagamento status;

    public StatusPagamento statusEm(Instant agora) {
        if (status == StatusPagamento.PENDENTE && agora.isAfter(criacao.plus(PRAZO))) {
            return StatusPagamento.EXPIRADO;
        }
        return status;
    }

}

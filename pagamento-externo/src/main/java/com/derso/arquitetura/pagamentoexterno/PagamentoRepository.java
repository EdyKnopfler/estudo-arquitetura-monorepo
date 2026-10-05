package com.derso.arquitetura.pagamentoexterno;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.derso.arquitetura.pagamentoexterno.entity.Pagamento;

public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {

    Optional<Pagamento> findByIdClienteAndChaveIdempotencia(String idCliente, UUID chaveIdempotencia);

    Optional<Pagamento> findByIdAndIdCliente(UUID id, String idCliente);

    // ON CONFLICT: par (cliente, chave) já existe — quem chamou só lê a transação existente.
    // criacao em UTC, igual ao que o Hibernate grava/lê (hibernate.jdbc.time_zone).
    @Modifying
    @Query(nativeQuery = true, value = """
        INSERT INTO pagamentos (id, id_cliente, chave_idempotencia, metodo, valor, criacao, status)
        VALUES (:id, :idCliente, :chave, :metodo, :valor, now() at time zone 'utc', 'PENDENTE')
        ON CONFLICT (id_cliente, chave_idempotencia) DO NOTHING
    """)
    int criarSeNaoExistir(
        @Param("id") UUID id,
        @Param("idCliente") String idCliente,
        @Param("chave") UUID chave,
        @Param("metodo") String metodo,
        @Param("valor") BigDecimal valor
    );

    // Prazo no WHERE: quem leu PENDENTE no último segundo não paga depois de vencer
    @Modifying
    @Query("""
        UPDATE Pagamento p
        SET p.status = 'PAGO'
        WHERE p.id = :id
            AND p.status = 'PENDENTE'
            AND p.criacao > :criadoDepoisDe
    """)
    int marcarPago(@Param("id") UUID id, @Param("criadoDepoisDe") Instant criadoDepoisDe);

    @Modifying
    @Query("""
        UPDATE Pagamento p
        SET p.status = 'CANCELADO'
        WHERE p.id = :id
            AND p.status = 'PENDENTE'
    """)
    int cancelar(@Param("id") UUID id);

}

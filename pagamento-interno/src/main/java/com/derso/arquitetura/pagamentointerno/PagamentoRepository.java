package com.derso.arquitetura.pagamentointerno;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.derso.arquitetura.pagamentointerno.entity.Pagamento;

public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {

    Optional<Pagamento> findByIdSessaoCompra(UUID idSessaoCompra);

    // ON CONFLICT: duas chamadas simultâneas pra mesma sessão — a segunda só lê a linha da primeira
    @Modifying
    @Query(nativeQuery = true, value = """
        INSERT INTO pagamentos (id, id_sessao_compra, id_reserva_hotel, id_reserva_voo_ida, id_reserva_voo_volta,
                                status, chave_idempotencia)
        VALUES (:id, :idSessaoCompra, :idReservaHotel, :idReservaVooIda, :idReservaVooVolta,
                'CRIANDO', :chave)
        ON CONFLICT (id_sessao_compra) DO NOTHING
    """)
    int criarSeNaoExistir(
        @Param("id") UUID id,
        @Param("idSessaoCompra") UUID idSessaoCompra,
        @Param("idReservaHotel") UUID idReservaHotel,
        @Param("idReservaVooIda") UUID idReservaVooIda,
        @Param("idReservaVooVolta") UUID idReservaVooVolta,
        @Param("chave") UUID chave
    );

    // Guardas por chave: chamada concorrente que já trocou de tentativa não é sobrescrita
    @Modifying
    @Query("""
        UPDATE Pagamento p
        SET
            p.status = 'AGUARDANDO_PAGAMENTO',
            p.idExterno = :idExterno,
            p.urlPagamento = :urlPagamento
        WHERE p.id = :id
            AND p.chaveIdempotencia = :chave
            AND p.status = 'CRIANDO'
    """)
    int registrarUrl(
        @Param("id") UUID id,
        @Param("chave") UUID chave,
        @Param("idExterno") UUID idExterno,
        @Param("urlPagamento") String urlPagamento
    );

    @Modifying
    @Query("""
        UPDATE Pagamento p
        SET p.chaveIdempotencia = :novaChave
        WHERE p.id = :id
            AND p.chaveIdempotencia = :chaveAtual
            AND p.status = 'CRIANDO'
    """)
    int trocarChave(@Param("id") UUID id, @Param("chaveAtual") UUID chaveAtual, @Param("novaChave") UUID novaChave);

}

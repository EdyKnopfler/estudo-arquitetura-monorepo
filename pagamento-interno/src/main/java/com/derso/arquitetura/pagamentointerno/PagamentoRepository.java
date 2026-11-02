package com.derso.arquitetura.pagamentointerno;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.derso.arquitetura.pagamentointerno.entity.Pagamento;

public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {

    Optional<Pagamento> findByIdSessaoCompra(UUID idSessaoCompra);

    Optional<Pagamento> findByIdExterno(UUID idExterno);

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
            p.urlPagamento = :urlPagamento,
            p.vencimento = :vencimento,
            p.tentativaEm = null
        WHERE p.id = :id
            AND p.chaveIdempotencia = :chave
            AND p.status = 'CRIANDO'
    """)
    int registrarUrl(
        @Param("id") UUID id,
        @Param("chave") UUID chave,
        @Param("idExterno") UUID idExterno,
        @Param("urlPagamento") String urlPagamento,
        @Param("vencimento") Instant vencimento
    );

    @Modifying
    @Query("""
        UPDATE Pagamento p
        SET
            p.chaveIdempotencia = :novaChave,
            p.tentativaEm = null
        WHERE p.id = :id
            AND p.chaveIdempotencia = :chaveAtual
            AND p.status = 'CRIANDO'
    """)
    int trocarChave(@Param("id") UUID id, @Param("chaveAtual") UUID chaveAtual, @Param("novaChave") UUID novaChave);

    // Uma chamada ao gateway por vez: ocupa se não há outra em voo dentro da janela
    @Modifying
    @Query("""
        UPDATE Pagamento p
        SET p.tentativaEm = :agora
        WHERE p.id = :id
            AND p.chaveIdempotencia = :chave
            AND p.status = 'CRIANDO'
            AND (p.tentativaEm IS NULL OR p.tentativaEm < :limite)
    """)
    int ocuparTentativa(
        @Param("id") UUID id,
        @Param("chave") UUID chave,
        @Param("agora") Instant agora,
        @Param("limite") Instant limite
    );

    @Modifying
    @Query("""
        UPDATE Pagamento p
        SET p.tentativaEm = null
        WHERE p.id = :id
            AND p.chaveIdempotencia = :chave
            AND p.status = 'CRIANDO'
    """)
    int liberarTentativa(@Param("id") UUID id, @Param("chave") UUID chave);

    // Só depois de cancelada/vencida no gateway. Guarda por id_externo: renovação concorrente troca uma vez só
    @Modifying
    @Query("""
        UPDATE Pagamento p
        SET
            p.status = 'CRIANDO',
            p.chaveIdempotencia = :novaChave,
            p.idExterno = null,
            p.urlPagamento = null,
            p.vencimento = null,
            p.tentativaEm = null
        WHERE p.id = :id
            AND p.idExterno = :idExterno
            AND p.status = 'AGUARDANDO_PAGAMENTO'
    """)
    int renovarTentativa(@Param("id") UUID id, @Param("idExterno") UUID idExterno, @Param("novaChave") UUID novaChave);

    // Estorno confirmado no gateway: a sessão volta pro pedido, então a linha volta pra tentativa nova
    // (mesmo efeito de renovarTentativa) — ESTORNADO só existe lá, no registro do gateway
    @Modifying
    @Transactional
    @Query("""
        UPDATE Pagamento p
        SET
            p.status = 'CRIANDO',
            p.chaveIdempotencia = :novaChave,
            p.idExterno = null,
            p.urlPagamento = null,
            p.vencimento = null,
            p.tentativaEm = null
        WHERE p.id = :id
            AND p.idExterno = :idExterno
            AND p.status = 'PAGO'
    """)
    int reabrirAposEstorno(@Param("id") UUID id, @Param("idExterno") UUID idExterno, @Param("novaChave") UUID novaChave);

    // Webhook pago: grava as intenções já ocupadas por ele. Sem linha afetada, webhook repetido
    @Modifying
    @Query("""
        UPDATE Pagamento p
        SET
            p.status = 'PAGO',
            p.sessaoPendente = true,
            p.filaPendente = true,
            p.intencoesOcupadasEm = :agora
        WHERE p.id = :id
            AND p.idExterno = :idExterno
            AND p.status = 'AGUARDANDO_PAGAMENTO'
    """)
    int registrarPago(@Param("id") UUID id, @Param("idExterno") UUID idExterno, @Param("agora") Instant agora);

    // Mesmo lease de ocuparTentativa: só uma execução das intenções por vez
    @Modifying
    @Query("""
        UPDATE Pagamento p
        SET p.intencoesOcupadasEm = :agora
        WHERE p.id = :id
            AND (p.sessaoPendente = true OR p.filaPendente = true)
            AND (p.intencoesOcupadasEm IS NULL OR p.intencoesOcupadasEm < :limite)
    """)
    int ocuparIntencoes(@Param("id") UUID id, @Param("agora") Instant agora, @Param("limite") Instant limite);

    @Modifying
    @Query("UPDATE Pagamento p SET p.sessaoPendente = false WHERE p.id = :id")
    int concluirIntencaoSessao(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Pagamento p SET p.filaPendente = false WHERE p.id = :id")
    int concluirIntencaoFila(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Pagamento p SET p.intencoesOcupadasEm = null WHERE p.id = :id")
    int liberarIntencoes(@Param("id") UUID id);

    @Query(nativeQuery = true, value = """
        SELECT id
        FROM pagamentos
        WHERE (sessao_pendente OR fila_pendente)
            AND (intencoes_ocupadas_em IS NULL OR intencoes_ocupadas_em < :limite)
        LIMIT :lote
    """)
    List<UUID> comIntencoesLivres(@Param("limite") Instant limite, @Param("lote") int lote);

}

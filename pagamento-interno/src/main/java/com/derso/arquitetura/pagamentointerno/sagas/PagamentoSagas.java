package com.derso.arquitetura.pagamentointerno.sagas;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.derso.arquitetura.pagamentointerno.PagamentoExternoService;
import com.derso.arquitetura.pagamentointerno.PagamentoRepository;
import com.derso.arquitetura.pagamentointerno.entity.Pagamento;
import com.derso.arquitetura.pagamentointerno.entity.StatusPagamento;
import com.derso.arquitetura.sagas.Encaminhamento;
import com.derso.arquitetura.sagas.Messaging;
import com.derso.arquitetura.sagas.ResultadoHandler;

import jakarta.persistence.EntityNotFoundException;

@Component
@Profile("sagas")
public class PagamentoSagas implements SmartLifecycle {

    @Value("${sagas.estafila}")
    public String estaFila;

    @Value("${sagas.filaanterior:#{null}}")
    public String filaAnterior;

    @Value("${sagas.proximafila:#{null}}")
    public String proximaFila;

    // StatusPagamento de pagamento-externo (módulo separado, sem tipos compartilhados)
    private static final String STATUS_ESTORNADO_NO_GATEWAY = "ESTORNADO";

    private final Messaging sagas;
    private final PagamentoExternoService servicoExterno;
    private final PagamentoRepository repositorio;

    private boolean running = false;

    public PagamentoSagas(Messaging sagas, PagamentoExternoService servicoExterno, PagamentoRepository repositorio) {
        this.sagas = sagas;
        this.servicoExterno = servicoExterno;
        this.repositorio = repositorio;
    }

    @Override
    public void start() {
        try {
            Optional<String> optFilaAnterior = Optional.ofNullable(filaAnterior);
            Optional<String> optProximaFila = Optional.ofNullable(proximaFila);

            sagas.configurarServico(estaFila, optFilaAnterior, optProximaFila);

            sagas.iniciarConsumo(
                estaFila + "-consumer",
                estaFila,
                optFilaAnterior,
                optProximaFila,
                mensagem -> {
                    double tipo = ((Number) mensagem.getOrDefault("tipo", Messaging.EXECUTE)).doubleValue();
                    Object rastreio = mensagem.get("rastreio");
                    UUID idPagamento = idPagamentoDaMensagem(mensagem);

                    Pagamento pagamento = repositorio.findById(idPagamento)
                        .orElseThrow(() -> new EntityNotFoundException("Pagamento não encontrado: " + idPagamento));

                    // EXECUTE: a cobrança já aconteceu no gateway (webhook gravou PAGO), só segue a cadeia
                    if (tipo == Messaging.EXECUTE) {
                        System.out.println("[pagamento] pago, seguindo — rastreio=" + rastreio);
                        return ResultadoHandler.ack(Encaminhamento.PARA_FRENTE);
                    }
                    return reverter(pagamento, rastreio);
                }
            );

            running = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // package-private pra testar direto, sem passar pela mensagem/repositório (mesmo padrão de ReservasSagas)
    ResultadoHandler reverter(Pagamento pagamento, Object rastreio) {
        // Fora de PAGO: já revertido (DESFACA repetido) — nada a estornar
        if (pagamento.getStatus() != StatusPagamento.PAGO) {
            System.out.println("[pagamento] já revertido (" + pagamento.getStatus() + "), seguindo — rastreio=" + rastreio);
            return ResultadoHandler.ack(Encaminhamento.PARA_TRAS);
        }

        try {
            String situacao = servicoExterno.estornar(pagamento.getIdExterno()).status();
            if (!STATUS_ESTORNADO_NO_GATEWAY.equals(situacao)) {
                throw new IllegalStateException("Estorno sem ESTORNADO no gateway: " + situacao);
            }
        } catch (Exception e) {
            // TODO erro catastrófico (cliente cobrado sem viagem) — desfecho em aberto, docs/todo.md.
            // Provisório: mensagem pra fila de erros, sem reverter a sessão.
            System.err.println("[pagamento] falha ao estornar — rastreio=" + rastreio + " — " + e.getMessage());
            return ResultadoHandler.nackDlq(Encaminhamento.NENHUM);
        }

        repositorio.reabrirAposEstorno(pagamento.getId(), pagamento.getIdExterno(), UUID.randomUUID());
        System.out.println("[pagamento] estornado — rastreio=" + rastreio);
        return ResultadoHandler.ack(Encaminhamento.PARA_TRAS);
    }

    // Id ausente ou inválido lança: o Messaging trata como erro não capturado (nack + DESFACA pra trás)
    static UUID idPagamentoDaMensagem(Map<String, Object> mensagem) {
        Object valor = mensagem.get("idPagamento");
        if (valor == null) {
            throw new IllegalArgumentException("Mensagem da SAGA sem idPagamento");
        }
        return UUID.fromString(valor.toString());
    }

    @Override
    public void stop() {
        sagas.pararConsumo();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

}

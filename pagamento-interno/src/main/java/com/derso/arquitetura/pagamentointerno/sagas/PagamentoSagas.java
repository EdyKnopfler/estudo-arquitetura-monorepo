package com.derso.arquitetura.pagamentointerno.sagas;

import java.io.IOException;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.derso.arquitetura.sagas.Encaminhamento;
import com.derso.arquitetura.sagas.ResultadoHandler;
import com.derso.arquitetura.sagas.Messaging;

@Component
@Profile("sagas")
public class PagamentoSagas implements SmartLifecycle {

    @Value("${sagas.estafila}")
    public String estaFila;

    @Value("${sagas.filaanterior:#{null}}")
    public String filaAnterior;

    @Value("${sagas.proximafila:#{null}}")
    public String proximaFila;

    private final Messaging sagas;

    private boolean running = false;

    public PagamentoSagas(Messaging sagas) throws IOException {
        this.sagas = sagas;
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

                    // TODO tratamento real: mesmo padrão de ReservasSagas (confirmar/estornar via serviço
                    // externo, 3 desfechos via ResultadoHandler). Precisa de idPagamento na mensagem pra
                    // achar a linha local (findById, mesmo banco — não é lookup pra evitar) e daí ler
                    // idExterno de lá. Ver docs/purchase-flow-design.md#payload-da-mensagem-da-saga.
                    double tipo = ((Number) mensagem.getOrDefault("tipo", Messaging.EXECUTE)).doubleValue();
                    Object rastreio = mensagem.get("rastreio");

                    if (tipo == Messaging.EXECUTE) {
                        System.out.println("[pagamento] confirmando cobrança — rastreio=" + rastreio);
                    } else {
                        System.out.println("[pagamento] ESTORNANDO pagamento — rastreio=" + rastreio);
                    }

                    return ResultadoHandler.ack(
                            tipo == Messaging.EXECUTE ? Encaminhamento.PARA_FRENTE : Encaminhamento.PARA_TRAS
                    );
                }
            );

            running = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

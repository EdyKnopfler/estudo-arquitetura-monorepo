package com.derso.arquitetura.sessaocompra.sagas;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.derso.arquitetura.sagas.Encaminhamento;
import com.derso.arquitetura.sagas.Messaging;
import com.derso.arquitetura.sagas.ResultadoHandler;
import com.derso.arquitetura.sessaocompra.SessaoCompraService;

// Uma fila pras duas pontas, decide pelo tipo — docs/purchase-flow-design.md#saga-estendida--sessaocompra-como-bookend-do-anel.
// Sem fila anterior nem próxima: nada segue daqui.
@Component
@Profile("sagas")
public class SessaoCompraSagas implements SmartLifecycle {

    @Value("${sagas.estafila}")
    public String estaFila;

    private final Messaging sagas;
    private final SessaoCompraService servico;

    private boolean running = false;

    public SessaoCompraSagas(Messaging sagas, SessaoCompraService servico) {
        this.sagas = sagas;
        this.servico = servico;
    }

    @Override
    public void start() {
        try {
            sagas.configurarServico(estaFila, Optional.empty(), Optional.empty());
            sagas.iniciarConsumo(estaFila + "-consumer", estaFila, Optional.empty(), Optional.empty(), this::tratar);
            running = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // package-private pra testar direto, sem broker
    ResultadoHandler tratar(Map<String, Object> mensagem) {
        double tipo = ((Number) mensagem.getOrDefault("tipo", Messaging.EXECUTE)).doubleValue();
        Object rastreio = mensagem.get("rastreio");
        UUID idSessao = idSessaoDaMensagem(mensagem);

        boolean feito = tipo == Messaging.EXECUTE
            ? servico.viagemReservada(idSessao)
            : servico.voltarAoPedido(idSessao);

        if (!feito) {
            // TODO sessão fora de PAGAMENTO_EFETUADO (ex.: PUT da sessão ainda com o job de pendências) — caso não
            // tratado. Provisório: fila de erros sem encaminhar; exceção aqui dispararia DESFACA e estornaria tudo.
            System.err.println("[sessaocompra] sessão " + idSessao + " fora do estado esperado — rastreio=" + rastreio);
            return ResultadoHandler.nackDlq(Encaminhamento.NENHUM);
        }

        System.out.println("[sessaocompra] " + (tipo == Messaging.EXECUTE ? "viagem reservada" : "voltou ao pedido")
            + " — rastreio=" + rastreio);
        return ResultadoHandler.ack(Encaminhamento.NENHUM);
    }

    // Id ausente ou inválido lança: o Messaging dá nack (sem fila anterior, não publica DESFACA)
    static UUID idSessaoDaMensagem(Map<String, Object> mensagem) {
        Object valor = mensagem.get("idSessaoCompra");
        if (valor == null) {
            throw new IllegalArgumentException("Mensagem da SAGA sem idSessaoCompra");
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

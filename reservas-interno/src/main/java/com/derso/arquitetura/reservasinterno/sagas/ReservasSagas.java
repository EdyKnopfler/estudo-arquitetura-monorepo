package com.derso.arquitetura.reservasinterno.sagas;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import com.derso.arquitetura.reservasinterno.ReservasExternoService;
import com.derso.arquitetura.reservasinterno.ReservasRepository;
import com.derso.arquitetura.reservasinterno.entity.Reserva;
import com.derso.arquitetura.sagas.Encaminhamento;
import com.derso.arquitetura.sagas.Messaging;
import com.derso.arquitetura.sagas.ResultadoHandler;

import jakarta.persistence.EntityNotFoundException;

@Component
@Profile("sagas")
public class ReservasSagas implements SmartLifecycle {

    private final ConsumersSaga consumersSaga;
    private final Messaging sagas;
    private final ReservasExternoService servicoExterno;
    private final ReservasRepository repositorio;

    private boolean running = false;

    public ReservasSagas(ConsumersSaga consumersSaga, Messaging sagas, ReservasExternoService servicoExterno, ReservasRepository repositorio) {
        this.consumersSaga = consumersSaga;
        this.sagas = sagas;
        this.servicoExterno = servicoExterno;
        this.repositorio = repositorio;
    }

    @Override
    public void start() {
        try {
            for (ConsumersSaga.Consumer consumer : consumersSaga.consumers()) {
                iniciarConsumer(consumer);
            }
            running = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void iniciarConsumer(ConsumersSaga.Consumer consumer) throws IOException {
        Optional<String> optFilaAnterior = Optional.ofNullable(consumer.filaanterior());
        Optional<String> optProximaFila = Optional.ofNullable(consumer.proximafila());

        sagas.configurarServico(consumer.estafila(), optFilaAnterior, optProximaFila);

        sagas.iniciarConsumo(
            consumer.estafila() + "-consumer",
            consumer.estafila(),
            optFilaAnterior,
            optProximaFila,
            mensagem -> {
                double tipo = ((Number) mensagem.getOrDefault("tipo", Messaging.EXECUTE)).doubleValue();
                Object rastreio = mensagem.get("rastreio");
                UUID idReserva = idReservaDaMensagem(mensagem, consumer.campoid());

                // findById local (mesmo processo/banco do papel web deste domínio) — não é chamada de
                // rede, é só a PK indexada. idExterno nunca sai daqui pra fora. Ver docs/purchase-flow-design.md.
                Reserva reserva = repositorio.findById(idReserva)
                    .orElseThrow(() -> new EntityNotFoundException("Reserva não encontrada: " + idReserva));

                return (tipo == Messaging.EXECUTE)
                        ? confirmar(reserva, consumer.estafila(), rastreio)
                        : reverter(reserva, consumer.estafila(), rastreio);
            }
        );
    }

    // package-private (não private) pra dar pra testar direto, sem passar pela mensagem/repositório.
    ResultadoHandler confirmar(Reserva reserva, String fila, Object rastreio) {
        try {
            servicoExterno.confirmar(reserva.getIdExterno());
            System.out.println("[" + fila + "] confirmado — rastreio=" + rastreio);

            repositorio.marcarReservada(reserva.getId());
            return ResultadoHandler.ack(Encaminhamento.PARA_FRENTE);
        } catch (HttpClientErrorException.NotFound e) {
            // falha de negócio: guarda WHERE confirmado=false do reservas-externo rejeitou — reserva não existe
            // mais/expirou/já foi confirmada. Resposta definitiva, não é ambíguo. Ver docs/purchase-flow-design.md.
            System.out.println("[" + fila + "] falha de negócio ao confirmar — rastreio=" + rastreio);

            // TODO status de falha ainda sem nome — docs/todo.md
            return ResultadoHandler.ack(Encaminhamento.PARA_TRAS);
        } catch (Exception e) {
            // falha geral: timeout/infra/5xx — não dá pra saber se o confirmar aconteceu do outro lado.
            // Ambíguo até existir o endpoint `consultar` + a task de retentativa (ver docs/todo.md); por ora
            // só ack e não avança em nenhuma direção — mensagem fica "perdida" até esse trabalho existir.
            System.err.println("[" + fila + "] falha geral ao confirmar — rastreio=" + rastreio + " — " + e.getMessage());
            return ResultadoHandler.ack(Encaminhamento.NENHUM);
        }
    }

    // package-private, mesmo motivo de confirmar() acima.
    // Quem recebe DESFACA confirmou antes e não foi quem falhou: volta pra pré-reserva. A compensação
    // segue pra trás em qualquer desfecho daqui.
    ResultadoHandler reverter(Reserva reserva, String fila, Object rastreio) {
        try {
            servicoExterno.desconfirmar(reserva.getIdExterno());
            System.out.println("[" + fila + "] revertido pra pré-reserva — rastreio=" + rastreio);
            repositorio.marcarPreReservada(reserva.getId());
        } catch (HttpClientErrorException.NotFound e) {
            // lá já não está confirmada (ex.: DESFACA repetido) — o externo é a fonte da verdade
            System.out.println("[" + fila + "] já não estava confirmada no externo — rastreio=" + rastreio);
            repositorio.marcarPreReservada(reserva.getId());
        } catch (Exception e) {
            // TODO falha ambígua: confirmada não tem TTL lá, pode ficar presa — robustez em docs/todo.md
            System.err.println("[" + fila + "] falha ao reverter, seguindo — rastreio=" + rastreio + " — " + e.getMessage());
        }

        return ResultadoHandler.ack(Encaminhamento.PARA_TRAS);
    }

    // Id ausente ou inválido lança: o Messaging trata como erro não capturado (nack + DESFACA pra trás)
    static UUID idReservaDaMensagem(Map<String, Object> mensagem, String campoId) {
        Object valor = mensagem.get(campoId);
        if (valor == null) {
            throw new IllegalArgumentException("Mensagem da SAGA sem " + campoId);
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

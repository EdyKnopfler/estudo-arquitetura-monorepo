package com.derso.arquitetura.reservasinterno.sagas;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
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

    @Value("${sagas.estafila}")
    public String estaFila;

    @Value("${sagas.filaanterior:#{null}}")
    public String filaAnterior;

    @Value("${sagas.proximafila:#{null}}")
    public String proximaFila;

    private final Messaging sagas;
    private final ReservasExternoService servicoExterno;
    private final ReservasRepository repositorio;

    private boolean running = false;

    public ReservasSagas(Messaging sagas, ReservasExternoService servicoExterno, ReservasRepository repositorio) throws IOException {
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
                    UUID idReserva = idReservaDaMensagem(mensagem);

                    // findById local (mesmo processo/banco do papel web deste domínio) — não é chamada de
                    // rede, é só a PK indexada. idExterno nunca sai daqui pra fora. Ver docs/purchase-flow-design.md.
                    Reserva reserva = repositorio.findById(idReserva)
                        .orElseThrow(() -> new EntityNotFoundException("Reserva não encontrada: " + idReserva));

                    return (tipo == Messaging.EXECUTE)
                            ? confirmar(reserva, rastreio)
                            : cancelar(reserva, rastreio);
                }
            );

            running = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // package-private (não private) pra dar pra testar direto, sem passar pela mensagem/repositório.
    ResultadoHandler confirmar(Reserva reserva, Object rastreio) {
        try {
            servicoExterno.confirmar(reserva.getIdExterno());
            System.out.println("[" + estaFila + "] confirmado — rastreio=" + rastreio);

            atualizarStatusReserva(reserva.getId(), "RESERVADA");
            return ResultadoHandler.ack(Encaminhamento.PARA_FRENTE);
        } catch (HttpClientErrorException.NotFound e) {
            // falha de negócio: guarda WHERE confirmado=false do reservas-externo rejeitou — reserva não existe
            // mais/expirou/já foi confirmada. Resposta definitiva, não é ambíguo. Ver docs/purchase-flow-design.md.
            System.out.println("[" + estaFila + "] falha de negócio ao confirmar — rastreio=" + rastreio);

            atualizarStatusReserva(reserva.getId(), "FALHA");
            return ResultadoHandler.ack(Encaminhamento.PARA_TRAS);
        } catch (Exception e) {
            // falha geral: timeout/infra/5xx — não dá pra saber se o confirmar aconteceu do outro lado.
            // Ambíguo até existir o endpoint `consultar` + a task de retentativa (ver docs/todo.md); por ora
            // só ack e não avança em nenhuma direção — mensagem fica "perdida" até esse trabalho existir.
            System.err.println("[" + estaFila + "] falha geral ao confirmar — rastreio=" + rastreio + " — " + e.getMessage());
            return ResultadoHandler.ack(Encaminhamento.NENHUM);
        }
    }

    // package-private, mesmo motivo de confirmar() acima.
    ResultadoHandler cancelar(Reserva reserva, Object rastreio) {
        // Melhor esforço — TTL do reservas-externo é a rede de segurança (axioma 1, docs/purchase-flow-design.md).
        // Por isso não se ramifica em falha de negócio/geral como o confirmar: sucesso ou falha aqui,
        // a compensação sempre segue pra trás.
        try {
            servicoExterno.cancelar(reserva.getIdExterno());
            System.out.println("[" + estaFila + "] cancelado — rastreio=" + rastreio);
        } catch (Exception e) {
            System.err.println("[" + estaFila + "] falha ao cancelar, seguindo (melhor esforço) — rastreio=" + rastreio + " — " + e.getMessage());
        }

        atualizarStatusReserva(reserva.getId(), "CANCELADA");
        return ResultadoHandler.ack(Encaminhamento.PARA_TRAS);
    }

    // TODO Reserva ainda não tem coluna de status (migration já tem "-- TODO falta status") — ver docs/todo.md,
    // "Dual-write pagamento/reservas". Nomes de status ainda em aberto; strings aqui são só placeholder.
    private void atualizarStatusReserva(UUID idReserva, String novoStatus) {
    }

    // TODO mensagem da SAGA ainda não carrega nenhum id (só `rastreio`, opaco) — ver
    // docs/purchase-flow-design.md#payload-da-mensagem-da-saga e docs/todo.md.
    UUID idReservaDaMensagem(Map<String, Object> mensagem) {
        throw new UnsupportedOperationException("id de reserva ainda não existe na mensagem da SAGA");
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

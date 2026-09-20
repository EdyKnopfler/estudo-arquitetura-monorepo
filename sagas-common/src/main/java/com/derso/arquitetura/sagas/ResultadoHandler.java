package com.derso.arquitetura.sagas;

public record ResultadoHandler(RetornoBroker retornoBroker, Encaminhamento encaminhamento) {

    public static ResultadoHandler ack(Encaminhamento encaminhamento) {
        return new ResultadoHandler(RetornoBroker.ACK, encaminhamento);
    }

    public static ResultadoHandler nackDlq(Encaminhamento encaminhamento) {
        return new ResultadoHandler(RetornoBroker.NACK_DLQ, encaminhamento);
    }
}

package com.derso.arquitetura.sagas;

import java.util.Map;

public interface SagaMessageHandler {

    void handle(Map<String, Object> mensagem) throws Exception;

}
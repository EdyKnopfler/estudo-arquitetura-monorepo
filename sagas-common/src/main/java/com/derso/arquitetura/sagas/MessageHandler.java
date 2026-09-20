package com.derso.arquitetura.sagas;

import java.util.Map;

public interface MessageHandler {

    ResultadoHandler handle(Map<String, Object> mensagem) throws Exception;

}
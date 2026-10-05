package com.derso.arquitetura.webbase.http;

import java.time.Duration;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

// Toda chamada HTTP entre serviços passa por aqui: sem timeout, um hang do outro lado trava quem
// chamou sem exceção nenhuma (o catch nunca dispara). Valores generosos pra chamadas de um insert.
public final class TimeoutHttp {

    public static final Duration CONEXAO = Duration.ofSeconds(3);
    public static final Duration LEITURA = Duration.ofSeconds(5);

    private TimeoutHttp() {
    }

    public static ClientHttpRequestFactory padrao() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONEXAO);
        requestFactory.setReadTimeout(LEITURA);
        return requestFactory;
    }

}

package com.derso.arquitetura.sessaocompra.sagas;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import com.derso.arquitetura.sagas.JacksonConfig;
import com.derso.arquitetura.sagas.Messaging;
import com.derso.arquitetura.sagas.RabbitConfig;

/**
 * Ponte pro sagas-common: a lib fica sem @Profile, agnóstica a quem a consome.
 * Só o papel "sagas" conecta no RabbitMQ — sessaocompra é a ponta final da cadeia, nas duas direções.
 */
@Configuration
@Profile("sagas")
@Import({ JacksonConfig.class, RabbitConfig.class, Messaging.class })
public class SagasWiring {
}

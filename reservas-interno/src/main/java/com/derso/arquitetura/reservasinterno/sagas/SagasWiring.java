package com.derso.arquitetura.reservasinterno.sagas;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import com.derso.arquitetura.sagas.RabbitConfig;
import com.derso.arquitetura.sagas.JacksonConfig;
import com.derso.arquitetura.sagas.Messaging;

/**
 * Ponte pro sagas-common: a lib fica sem @Profile, agnóstica a quem a consome.
 * Só quem precisa da coreografia (papel "sagas") importa a conexão RabbitMQ.
 */
@Configuration
@Profile("sagas")
@Import({ JacksonConfig.class, RabbitConfig.class, Messaging.class })
public class SagasWiring {
}

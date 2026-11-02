package com.derso.arquitetura.pagamentointerno.sagas;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import com.derso.arquitetura.sagas.RabbitConfig;
import com.derso.arquitetura.sagas.JacksonConfig;
import com.derso.arquitetura.sagas.Messaging;

/**
 * Ponte pro sagas-common: a lib fica sem @Profile, agnóstica a quem a consome.
 * Papel "sagas" consome e publica; "web" e "pendencias" só publicam a 1ª mensagem (WebhookService).
 */
@Configuration
@Profile({ "web", "sagas", "pendencias" })
@Import({ JacksonConfig.class, RabbitConfig.class, Messaging.class })
public class SagasWiring {
}

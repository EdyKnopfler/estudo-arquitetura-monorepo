package com.derso.arquitetura.pagamentointerno.sagas;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import com.derso.arquitetura.sagas.RabbitConfig;
import com.derso.arquitetura.sagas.SagasJacksonConfig;
import com.derso.arquitetura.sagas.SagasMessaging;

/**
 * Ponte pro sagas-common: a lib fica sem @Profile, agnóstica a quem a consome.
 * Papel "sagas" consome e publica; papel "web" só publica a 1ª mensagem no webhook.
 */
@Configuration
@Profile({ "web", "sagas" })
@Import({ SagasJacksonConfig.class, RabbitConfig.class, SagasMessaging.class })
public class SagasWiring {
}

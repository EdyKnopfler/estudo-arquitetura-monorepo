package com.derso.arquitetura.reservasexterno.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SimuladorTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
        .withUserConfiguration(SimuladorAleatorio.class, SimuladorDeTeste.class);

    @Test
    void foraDoProfileTestUsaSorteio() {
        contexto.withPropertyValues("simulacao.resultado=FALHA_INFRA").run(ctx ->
            assertThat(ctx.getBean(Simulador.class)).isInstanceOf(SimuladorAleatorio.class)
        );
    }

    @Test
    void noProfileTestHeaderVenceConfiguracao() {
        contexto.withPropertyValues("spring.profiles.active=test", "simulacao.resultado=FALHA_INFRA").run(ctx -> {
            Simulador simulador = ctx.getBean(Simulador.class);

            assertEquals(ResultadoSimulado.SUCESSO, simulador.decidir("SUCESSO"));
            assertEquals(ResultadoSimulado.FALHA_INFRA, simulador.decidir(null));
        });
    }

}

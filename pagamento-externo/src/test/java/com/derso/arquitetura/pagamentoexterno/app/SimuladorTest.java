package com.derso.arquitetura.pagamentoexterno.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SimuladorTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
        .withUserConfiguration(SimuladorAleatorio.class, SimuladorDeTeste.class);

    @Test
    void foraDoProfileTestIgnoraHeaderEConfiguracao() {
        contexto.withPropertyValues("simulacao.resultado=FALHA_NEGOCIO").run(ctx -> {
            Simulador simulador = ctx.getBean(Simulador.class);
            assertThat(simulador).isInstanceOf(SimuladorAleatorio.class);

            // sorteio só produz SUCESSO/FALHA_INFRA — FALHA_NEGOCIO só sai se o pedido fosse obedecido
            for (int i = 0; i < 50; i++) {
                assertNotEquals(ResultadoSimulado.FALHA_NEGOCIO, simulador.decidir("FALHA_NEGOCIO"));
            }
        });
    }

    @Test
    void noProfileTestHeaderVenceConfiguracao() {
        contexto.withPropertyValues("spring.profiles.active=test", "simulacao.resultado=FALHA_INFRA").run(ctx -> {
            Simulador simulador = ctx.getBean(Simulador.class);

            assertEquals(ResultadoSimulado.FALHA_NEGOCIO, simulador.decidir("FALHA_NEGOCIO"));
            assertEquals(ResultadoSimulado.FALHA_INFRA, simulador.decidir(null));
        });
    }

    @Test
    void noProfileTestSemHeaderNemConfiguracaoSorteia() {
        contexto.withPropertyValues("spring.profiles.active=test").run(ctx -> {
            Simulador simulador = ctx.getBean(Simulador.class);

            for (int i = 0; i < 50; i++) {
                assertNotEquals(ResultadoSimulado.FALHA_NEGOCIO, simulador.decidir(null));
            }
        });
    }

}

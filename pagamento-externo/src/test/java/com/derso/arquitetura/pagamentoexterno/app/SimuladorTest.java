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
    void foraDoProfileTestIgnoraConfiguracao() {
        contexto.withPropertyValues("simulacao.resultado=FALHA_NEGOCIO").run(ctx -> {
            Simulador simulador = ctx.getBean(Simulador.class);
            assertThat(simulador).isInstanceOf(SimuladorAleatorio.class);

            // sorteio só produz SUCESSO/FALHA_INFRA — FALHA_NEGOCIO só sai se a configuração fosse obedecida
            for (int i = 0; i < 50; i++) {
                assertNotEquals(ResultadoSimulado.FALHA_NEGOCIO, simulador.decidir());
            }
        });
    }

    @Test
    void noProfileTestObedeceConfiguracao() {
        contexto.withPropertyValues("spring.profiles.active=test", "simulacao.resultado=FALHA_NEGOCIO").run(ctx ->
            assertEquals(ResultadoSimulado.FALHA_NEGOCIO, ctx.getBean(Simulador.class).decidir())
        );
    }

    @Test
    void noProfileTestSemConfiguracaoSorteia() {
        contexto.withPropertyValues("spring.profiles.active=test").run(ctx -> {
            Simulador simulador = ctx.getBean(Simulador.class);

            for (int i = 0; i < 50; i++) {
                assertNotEquals(ResultadoSimulado.FALHA_NEGOCIO, simulador.decidir());
            }
        });
    }

}

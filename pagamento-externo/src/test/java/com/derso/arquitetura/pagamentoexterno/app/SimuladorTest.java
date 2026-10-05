package com.derso.arquitetura.pagamentoexterno.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SimuladorTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
        .withUserConfiguration(SimuladorAleatorio.class, SimuladorDeTeste.class);

    @Test
    void foraDoProfileTestIgnoraConfiguracao() {
        contexto.withPropertyValues("simulacao.resultado=FALHA_ANTES_DE_GRAVAR").run(ctx -> {
            Simulador simulador = ctx.getBean(Simulador.class);
            assertThat(simulador).isInstanceOf(SimuladorAleatorio.class);
            assertTrue(sorteiaAlgumSucesso(simulador));
        });
    }

    @Test
    void noProfileTestObedeceConfiguracao() {
        contexto.withPropertyValues("spring.profiles.active=test", "simulacao.resultado=FALHA_DEPOIS_DE_GRAVAR").run(ctx ->
            assertEquals(ResultadoSimulado.FALHA_DEPOIS_DE_GRAVAR, ctx.getBean(Simulador.class).decidir())
        );
    }

    @Test
    void noProfileTestSemConfiguracaoSorteia() {
        contexto.withPropertyValues("spring.profiles.active=test").run(ctx ->
            assertTrue(sorteiaAlgumSucesso(ctx.getBean(Simulador.class)))
        );
    }

    @Test
    void sorteioProduzOsDoisTiposDeFalha() {
        var resultados = IntStream.range(0, 1000).mapToObj(i -> Simulador.sortear()).distinct().toList();

        assertThat(resultados).containsExactlyInAnyOrder(ResultadoSimulado.values());
    }

    @Test
    void noProfileTestPagarObedeceConfiguracaoPropria() {
        contexto.withPropertyValues("spring.profiles.active=test", "simulacao.resultado=SUCESSO", "simulacao.resultado-pagar=RECUSADO").run(ctx -> {
            Simulador simulador = ctx.getBean(Simulador.class);
            assertEquals(ResultadoSimulado.SUCESSO, simulador.decidir());
            assertEquals(ResultadoPagar.RECUSADO, simulador.decidirPagar());
        });
    }

    @Test
    void sorteioDoPagarProduzOsDoisTiposDeFalha() {
        var resultados = IntStream.range(0, 1000).mapToObj(i -> Simulador.sortearPagar()).distinct().toList();

        assertThat(resultados).containsExactlyInAnyOrder(ResultadoPagar.values());
    }

    // 50 falhas seguidas com 25% de chance cada: probabilidade desprezível
    private static boolean sorteiaAlgumSucesso(Simulador simulador) {
        return IntStream.range(0, 50).anyMatch(i -> simulador.decidir() == ResultadoSimulado.SUCESSO);
    }

}

package com.derso.arquitetura.sessaocompra.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;

// Teste estrutural, sem contexto Spring: garante que ninguém adicione um novo endpoint por
// sessão em SessaoCompraController sem @PreAuthorize de ownership — não depende de lembrar
// de escrever a checagem à mão em cada método novo, o teste quebra sozinho se esquecerem.
class SessaoCompraControllerOwnershipGuardTest {

    // pagamentoEfetuado é candidato a virar consumidor de fila SAGA (não chamada de cliente
    // via JWT) — ver comentário no controller e docs/purchase-flow-design.md.
    private static final Set<String> ISENTOS_DE_OWNERSHIP = Set.of("pagamentoEfetuado");

    @Test
    void todoEndpointComIdDeSessaoExigeOwnershipViaPreAuthorize() {
        List<String> semProtecao = Arrays.stream(SessaoCompraController.class.getDeclaredMethods())
            .filter(SessaoCompraControllerOwnershipGuardTest::recebeIdDeSessaoNoPath)
            .filter(m -> !ISENTOS_DE_OWNERSHIP.contains(m.getName()))
            .filter(m -> m.getAnnotation(PreAuthorize.class) == null)
            .map(m -> m.getName())
            .toList();

        assertTrue(semProtecao.isEmpty(), "endpoints sem @PreAuthorize de ownership: " + semProtecao);
    }

    private static boolean recebeIdDeSessaoNoPath(Method metodo) {
        for (Parameter parametro : metodo.getParameters()) {
            PathVariable pathVariable = parametro.getAnnotation(PathVariable.class);
            if (pathVariable != null && parametro.getType() == UUID.class) {
                return true;
            }
        }
        return false;
    }

}

package com.derso.arquitetura.webbase.internalclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class InternalClientsConfigTest {

    @Test
    void listaDeClientesViraMapaClientIdParaSecret() {
        InternalClientsConfig config = bind(Map.of(
            "internal-backend.clients[0].client-id", "reservas-interno",
            "internal-backend.clients[0].client-secret", "segredo-1",
            "internal-backend.clients[1].client-id", "pagamento-externo",
            "internal-backend.clients[1].client-secret", "segredo-2"
        ));

        Map<String, String> mapa = config.getClientsAsMap();

        assertEquals(2, mapa.size());
        assertEquals("segredo-1", mapa.get("reservas-interno"));
        assertEquals("segredo-2", mapa.get("pagamento-externo"));
    }

    @Test
    void clientIdDuplicadoFalhaNoBoot() {
        Exception e = assertThrows(Exception.class, () -> bind(Map.of(
            "internal-backend.clients[0].client-id", "reservas-interno",
            "internal-backend.clients[0].client-secret", "segredo-1",
            "internal-backend.clients[1].client-id", "reservas-interno",
            "internal-backend.clients[1].client-secret", "segredo-2"
        )));

        assertTrue(causaContem(e, "Duplicate key"),
            "esperava a cadeia de causas mencionar chave duplicada, mas foi: " + e);
    }

    private static boolean causaContem(Throwable e, String trecho) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(trecho)) {
                return true;
            }
        }
        return false;
    }

    private static InternalClientsConfig bind(Map<String, String> propriedades) {
        InternalClientsConfig config = new InternalClientsConfig();
        Binder binder = new Binder(new MapConfigurationPropertySource(propriedades));
        return binder.bind("internal-backend", Bindable.ofInstance(config)).get();
    }
}

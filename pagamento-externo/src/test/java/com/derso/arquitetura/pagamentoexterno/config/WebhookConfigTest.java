package com.derso.arquitetura.pagamentoexterno.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class WebhookConfigTest {

    // lido da própria anotação, não copiado à mão — se o prefixo divergir de novo do
    // application.yaml (bug original: "webhooks" isolado vs. "internal-backend.webhooks"
    // aninhado), o bind() abaixo não encontra nada e o teste falha, em vez de silenciosamente
    // testar um prefixo que não é o de produção
    private static final String PREFIX = WebhookConfig.class.getAnnotation(ConfigurationProperties.class).prefix();

    @Test
    void listaDeWebhooksViraMapasPorClientId() {
        WebhookConfig config = bind(Map.of(
            "internal-backend.webhooks[0].url", "http://pagamento-interno:8087/webhook",
            "internal-backend.webhooks[0].client-id", "pagamentoInternoId",
            "internal-backend.webhooks[0].client-secret", "pagamentoInternoSecret"
        ));

        assertEquals("http://pagamento-interno:8087/webhook", config.getUrlsById().get("pagamentoInternoId"));
        assertEquals("pagamentoInternoSecret", config.getSecretsById().get("pagamentoInternoId"));
    }

    private static WebhookConfig bind(Map<String, String> propriedades) {
        WebhookConfig config = new WebhookConfig();
        Binder binder = new Binder(new MapConfigurationPropertySource(propriedades));
        return binder.bind(PREFIX, Bindable.ofInstance(config)).get();
    }
}

package com.derso.arquitetura.pagamentoexterno.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

// prefixo "internal-backend" porque é onde o application.yaml declara este bloco (junto de
// "clients", quem pode chamar este serviço) — não "webhooks" isolado, que não bate com o YAML
@Component
@ConfigurationProperties(prefix = "internal-backend")
public class WebhookConfig {

    private List<Webhook> webhooks = new ArrayList<>();
    private Map<String, String> urlsById = new HashMap<>();
    private Map<String, String> secretsById = new HashMap<>();

    public List<Webhook>  getWebhooks() {
        return webhooks;
    }

    public void setWebhooks(List<Webhook>  webhooks) {
        this.webhooks = webhooks;

        if (webhooks != null) {
            this.urlsById = webhooks.stream().collect(Collectors.toMap(
                w -> w.getClientId(),
                w -> w.getUrl()
            ));

            this.secretsById = webhooks.stream().collect(Collectors.toMap(
                w -> w.getClientId(),
                w -> w.getClientSecret()
            ));
        }
    }

    public Map<String, String> getUrlsById() {
        return urlsById;
    }

    public Map<String, String> getSecretsById() {
        return secretsById;
    }

    @Setter
    @Getter
    private static class Webhook {
        private String url;
        private String clientId;
        private String clientSecret;
    }
    
}

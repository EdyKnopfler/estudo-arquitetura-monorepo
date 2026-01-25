package com.derso.arquitetura.pagamentoexterno.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "internal-backend")
public class WebhookConfig {

    private Map<String, String> webhooks = new HashMap<>();

    public Map<String, String> getWebhooks() {
        return webhooks;
    }

    public void setWebhooks(Map<String, String> details) {
        this.webhooks = details;
    }
    
}

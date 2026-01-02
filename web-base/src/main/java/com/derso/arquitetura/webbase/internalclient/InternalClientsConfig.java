package com.derso.arquitetura.webbase.internalclient;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "internal-backend")
public class InternalClientsConfig {

    private Map<String, String> clients = new HashMap<>();

    public Map<String, String> getClients() {
        return clients;
    }

    public void setClients(Map<String, String> details) {
        this.clients = details;
    }
    
}

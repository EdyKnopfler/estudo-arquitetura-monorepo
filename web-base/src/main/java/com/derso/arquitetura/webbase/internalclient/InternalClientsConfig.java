package com.derso.arquitetura.webbase.internalclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "internal-backend")
public class InternalClientsConfig {

    private List<InternalClient> clients = new ArrayList<>();
    private Map<String, String> clientsAsMap = new HashMap<>();

    public void setClients(List<InternalClient> clients) {
        this.clients = clients;

        if (clients != null) {
            this.clientsAsMap = clients.stream().collect(Collectors.toMap(
                InternalClient::getClientId, 
                InternalClient::getClientSecret
            ));
        }
    }

    public List<InternalClient> getClients() {
        return clients;
    }

    public Map<String, String> getClientsAsMap() {
        return clientsAsMap;
    }

    @Setter
    @Getter
    private static class InternalClient {
        private String clientId;
        private String clientSecret;
    }
    
}

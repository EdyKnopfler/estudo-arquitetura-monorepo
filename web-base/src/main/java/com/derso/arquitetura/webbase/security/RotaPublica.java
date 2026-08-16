package com.derso.arquitetura.webbase.security;

import org.springframework.http.HttpMethod;

// Rota pública é decisão do endpoint (não muda entre ambientes), então fica em código —
// o serviço declara via @Bean List<RotaPublica> no seu próprio SecurityConfiguration,
// não em application.yaml.
public record RotaPublica(HttpMethod method, String pattern) {
}

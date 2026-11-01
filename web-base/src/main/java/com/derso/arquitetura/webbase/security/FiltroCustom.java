package com.derso.arquitetura.webbase.security;

// Rota que autentica com outra identidade que não a do security.auth-type — decisão do
// endpoint, então em código: o serviço declara via @Bean List<FiltroCustom> "customFilters".
public record FiltroCustom(String pattern, AuthType authType) {
}

package com.derso.arquitetura.webbase.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
public class ErrorHandlingAutoConfiguration {

    @Bean
    public TrataErros trataErros() {
        return new TrataErros();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.dao.DataIntegrityViolationException")
    static class ErrosDeBanco {

        @Bean
        public TrataErrosDeBanco trataErrosDeBanco() {
            return new TrataErrosDeBanco();
        }

    }

}

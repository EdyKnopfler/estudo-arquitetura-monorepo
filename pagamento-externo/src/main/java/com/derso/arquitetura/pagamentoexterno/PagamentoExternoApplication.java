package com.derso.arquitetura.pagamentoexterno;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.derso.arquitetura.pagamentoexterno", "com.derso.arquitetura.webbase.config", "com.derso.arquitetura.webbase.internalclient"})
public class PagamentoExternoApplication {

	public static final double CHANCE_FALHA = 0.25;

	public static void main(String[] args) {
		SpringApplication.run(PagamentoExternoApplication.class, args);
	}

}

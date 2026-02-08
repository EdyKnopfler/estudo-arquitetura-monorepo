package com.derso.arquitetura.pagamentointerno;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.derso.arquitetura.pagamentointerno", "com.derso.arquitetura.webbase.config", "com.derso.arquitetura.webbase.internalclient"})
public class PagamentoInternoWebApplication {

	public static void main(String[] args) {
		SpringApplication.run(PagamentoInternoWebApplication.class, args);
	}

}

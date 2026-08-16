package com.derso.arquitetura.pagamentoexterno;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PagamentoExternoApplication {

	public static final double CHANCE_FALHA = 0.25;

	public static void main(String[] args) {
		SpringApplication.run(PagamentoExternoApplication.class, args);
	}

}

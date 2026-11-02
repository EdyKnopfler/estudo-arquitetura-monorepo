package com.derso.arquitetura.pagamentointerno;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PagamentoInternoApplication {

	public static void main(String[] args) {
		SpringApplication.run(PagamentoInternoApplication.class, args);
	}

}

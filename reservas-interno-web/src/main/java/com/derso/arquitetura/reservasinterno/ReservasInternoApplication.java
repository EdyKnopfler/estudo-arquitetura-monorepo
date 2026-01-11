package com.derso.arquitetura.reservasinterno;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.derso.arquitetura.reservasinterno", "com.derso.arquitetura.webbase.config", "com.derso.arquitetura.webbase.internalclient"})
public class ReservasInternoApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReservasInternoApplication.class, args);
	}

}

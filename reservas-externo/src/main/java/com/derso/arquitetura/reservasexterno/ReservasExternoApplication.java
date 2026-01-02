package com.derso.arquitetura.reservasexterno;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.derso.arquitetura.reservasexterno", "com.derso.arquitetura.webbase.config", "com.derso.arquitetura.webbase.internalclient"})
public class ReservasExternoApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReservasExternoApplication.class, args);
	}

}

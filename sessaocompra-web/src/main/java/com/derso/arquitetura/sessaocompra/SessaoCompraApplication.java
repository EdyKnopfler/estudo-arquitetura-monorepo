package com.derso.arquitetura.sessaocompra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.derso.arquitetura.sessaocompra", "com.derso.arquitetura.webbase.config"})
public class SessaoCompraApplication {

	public static void main(String[] args) {
		SpringApplication.run(SessaoCompraApplication.class, args);
	}

}

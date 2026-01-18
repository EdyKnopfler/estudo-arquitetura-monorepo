package com.derso.arquitetura.reservasinternosagas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = {"com.derso.arquitetura"})
@ComponentScan(basePackages = {"com.derso.arquitetura"})
@EntityScan(basePackages = {"com.derso.arquitetura"})
public class ReservasInternoSagasApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReservasInternoSagasApplication.class, args);
	}

}

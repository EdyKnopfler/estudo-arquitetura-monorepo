package com.derso.arquitetura.timeout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories(basePackages = {"com.derso.arquitetura"})
@ComponentScan(basePackages = {"com.derso.arquitetura"})
@EntityScan(basePackages = {"com.derso.arquitetura"})
public class TimeoutApplication {

	public static void main(String[] args) {
		SpringApplication.run(TimeoutApplication.class, args);
	}

}

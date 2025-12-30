package com.derso.arquitetura.timeout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TimeoutApplication {

	public static void main(String[] args) {
		SpringApplication.run(TimeoutApplication.class, args);
	}

}

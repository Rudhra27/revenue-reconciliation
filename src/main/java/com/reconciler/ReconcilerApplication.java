package com.reconciler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ReconcilerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReconcilerApplication.class, args);
	}

}

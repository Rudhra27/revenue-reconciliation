package com.reconciler;

import com.reconciler.config.DatabaseUrl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ReconcilerApplication {

	public static void main(String[] args) {
		DatabaseUrl.applyToSystemProperties();
		SpringApplication.run(ReconcilerApplication.class, args);
	}

}

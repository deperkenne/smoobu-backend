package com.clean.dd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DdApplication {

	public static void main(String[] args) {
		SpringApplication.run(DdApplication.class, args);
	}

}

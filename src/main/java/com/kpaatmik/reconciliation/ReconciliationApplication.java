package com.kpaatmik.reconciliation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.kpaatmik.reconciliation.config.ReconciliationFileProperties;

@SpringBootApplication
@EnableConfigurationProperties(ReconciliationFileProperties.class)
public class ReconciliationApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReconciliationApplication.class, args);
	}

}

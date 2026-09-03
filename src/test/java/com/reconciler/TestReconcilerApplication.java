package com.reconciler;

import org.springframework.boot.SpringApplication;

/**
 * Local dev entry point: runs the app against a throwaway Postgres container so you
 * don't need a database installed. Not part of the deployed build.
 */
public class TestReconcilerApplication {

	public static void main(String[] args) {
		SpringApplication.from(ReconcilerApplication::main)
				.with(TestcontainersConfiguration.class)
				.run(args);
	}

}

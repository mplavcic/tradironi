package com.example.tradironi;

import org.springframework.boot.SpringApplication;
import org.springframework.modulith.Modulith;

@Modulith(
		systemName = "Tradironi",
		sharedModules = {"shared"}
)
public class TradironiApplication {
	public static void main(String[] args) {
		SpringApplication.run(TradironiApplication.class, args);
	}
}
package edu.campus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts core-app, the modular monolith. Each package directly below {@code edu.campus} is one
 * Spring Modulith module (spec: "one Modulith module = one top-level package").
 */
@SpringBootApplication
public class CoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
}

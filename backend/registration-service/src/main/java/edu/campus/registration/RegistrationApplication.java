package edu.campus.registration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts registration-service (port 8082). It is a separate app, not a core-app module, so it can
 * scale on its own during the registration rush. It may only use the {@code registration} schema
 * (spec D3).
 */
@SpringBootApplication
public class RegistrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(RegistrationApplication.class, args);
    }
}

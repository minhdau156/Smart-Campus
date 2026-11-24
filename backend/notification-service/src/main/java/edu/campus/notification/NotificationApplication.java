package edu.campus.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts notification-service (port 8083). It listens to events from other modules on Kafka and
 * turns them into notifications. It may only use the {@code notification} schema (spec D3).
 */
@SpringBootApplication
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}

package edu.campus.notification;

import edu.campus.starter.consumer.IdempotentConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The dedupe helper for every Kafka listener of this service (spec D2). Its table is
 * {@code notification.processed_events} (Flyway migration in db/migration/notification/).
 */
@Configuration(proxyBeanMethods = false)
class ConsumerConfiguration {

    @Bean
    IdempotentConsumer idempotentConsumer(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        return new IdempotentConsumer(jdbc, transactionManager, "notification");
    }
}

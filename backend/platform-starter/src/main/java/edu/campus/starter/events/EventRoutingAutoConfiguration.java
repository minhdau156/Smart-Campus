package edu.campus.starter.events;

import edu.campus.kernel.event.DomainEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.events.EventExternalizationConfiguration;

/**
 * Sends to Kafka exactly the events that have an {@link EventRoute}, each to its topic with its
 * key (spec D1, D6). Active only in an app that declares at least one route.
 *
 * <p>{@code beforeName}: our {@link EventExternalizationConfiguration} must exist before Modulith's
 * own auto-configuration looks for one, so Modulith uses ours instead of its default.
 */
@AutoConfiguration(beforeName = "org.springframework.modulith.events.config.EventExternalizationAutoConfiguration")
@ConditionalOnClass(EventExternalizationConfiguration.class)
@ConditionalOnBean(EventRoute.class)
public class EventRoutingAutoConfiguration {

    @Bean
    EventRouting eventRouting(ObjectProvider<EventRoute> routes) {
        return new EventRouting(routes.orderedStream().toList());
    }

    @Bean
    EventExternalizationConfiguration eventExternalizationConfiguration(EventRouting routing) {
        return EventExternalizationConfiguration.externalizing()
                .select(routing::isRouted)
                .route(DomainEvent.class, routing::target)
                .build();
    }

    @Bean
    EventKeyCheck eventKeyCheck(EventRouting routing) {
        return new EventKeyCheck(routing);
    }
}

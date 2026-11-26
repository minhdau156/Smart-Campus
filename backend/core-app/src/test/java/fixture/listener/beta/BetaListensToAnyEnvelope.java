package fixture.listener.beta;

import edu.campus.kernel.event.DomainEvent;
import org.springframework.context.event.EventListener;

/** Fixture (D5), VIOLATION: any envelope may carry another module's event, so it is not allowed. */
class BetaListensToAnyEnvelope {

    @EventListener
    void on(DomainEvent<?> event) {}
}

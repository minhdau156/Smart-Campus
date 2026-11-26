package fixture.listener.alpha;

import edu.campus.kernel.event.DomainEvent;
import org.springframework.context.event.EventListener;

/** Fixture (D5), allowed: alpha listens in-process to its OWN event, plain and in an envelope. */
class AlphaOwnListener {

    @EventListener
    void on(AlphaCreated event) {}

    @EventListener
    void onEnvelope(DomainEvent<AlphaCreated> event) {}
}

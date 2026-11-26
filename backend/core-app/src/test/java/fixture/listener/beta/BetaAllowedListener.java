package fixture.listener.beta;

import edu.campus.kernel.event.DomainEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.ApplicationModuleListener;

/** Fixture (D5), allowed: beta's own events, and Spring's own events (not from any module). */
class BetaAllowedListener {

    @ApplicationModuleListener
    void on(BetaChanged event) {}

    @EventListener
    void onEnvelope(DomainEvent<BetaChanged> event) {}

    @EventListener
    void onStartup(ApplicationReadyEvent event) {}
}

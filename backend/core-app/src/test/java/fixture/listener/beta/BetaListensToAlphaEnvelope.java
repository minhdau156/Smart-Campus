package fixture.listener.beta;

import edu.campus.kernel.event.DomainEvent;
import fixture.listener.alpha.AlphaCreated;
import org.springframework.transaction.event.TransactionalEventListener;

/** Fixture (D5), VIOLATION: alpha's event wrapped in the envelope is still alpha's event. */
class BetaListensToAlphaEnvelope {

    @TransactionalEventListener
    void on(DomainEvent<AlphaCreated> event) {}
}

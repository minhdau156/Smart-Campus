package fixture.listener.beta;

import fixture.listener.alpha.AlphaCreated;
import org.springframework.modulith.events.ApplicationModuleListener;

/** Fixture (D5), VIOLATION: beta handles alpha's event in-process instead of from Kafka. */
class BetaListensToAlphaDirectly {

    @ApplicationModuleListener
    void on(AlphaCreated event) {}
}

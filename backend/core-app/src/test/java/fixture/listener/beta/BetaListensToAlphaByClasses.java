package fixture.listener.beta;

import fixture.listener.alpha.AlphaCreated;
import org.springframework.context.event.EventListener;

/** Fixture (D5), VIOLATION: the event type is named in the annotation, not as a parameter. */
class BetaListensToAlphaByClasses {

    @EventListener(classes = AlphaCreated.class)
    void on() {}
}

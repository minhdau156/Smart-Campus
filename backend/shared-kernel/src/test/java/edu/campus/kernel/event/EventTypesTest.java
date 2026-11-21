package edu.campus.kernel.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class EventTypesTest {

    record EnrollmentConfirmed(String studentId) {}

    record GradePosted(String studentId) {}

    @Test
    void registeredTypeResolvesToItsPayloadClass() {
        EventTypes types = new EventTypes()
                .register("EnrollmentConfirmed", EnrollmentConfirmed.class)
                .register("GradePosted", GradePosted.class);

        assertThat(types.resolve("EnrollmentConfirmed")).isEqualTo(EnrollmentConfirmed.class);
        assertThat(types.resolve("GradePosted")).isEqualTo(GradePosted.class);
    }

    @Test
    void registeringTheSameTypeTwiceFails() {
        EventTypes types = new EventTypes().register("EnrollmentConfirmed", EnrollmentConfirmed.class);

        assertThatIllegalStateException()
                .isThrownBy(() -> types.register("EnrollmentConfirmed", GradePosted.class))
                .withMessageContaining("EnrollmentConfirmed")
                .withMessageContaining("already registered");
        // The first registration is kept.
        assertThat(types.resolve("EnrollmentConfirmed")).isEqualTo(EnrollmentConfirmed.class);
    }

    @Test
    void unknownTypeFails() {
        EventTypes types = new EventTypes();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> types.resolve("NoSuchEvent"))
                .withMessageContaining("NoSuchEvent");
    }

    @Test
    void isRegisteredTellsWhetherATypeIsKnown() {
        EventTypes types = new EventTypes().register("GradePosted", GradePosted.class);

        assertThat(types.isRegistered("GradePosted")).isTrue();
        assertThat(types.isRegistered("EnrollmentConfirmed")).isFalse();
    }

    @Test
    void nullsAreRejected() {
        EventTypes types = new EventTypes();

        assertThatNullPointerException().isThrownBy(() -> types.register(null, GradePosted.class));
        assertThatNullPointerException().isThrownBy(() -> types.register("GradePosted", null));
    }
}

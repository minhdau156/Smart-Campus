package edu.campus.kernel.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdsTest {

    /** The pattern every module follows for its own ids, e.g. {@code StudentId}, {@code SectionId}. */
    record SampleId(UUID value) implements TypedId {
        SampleId {
            Ids.requireId(value, "SampleId");
        }
    }

    @Test
    void newIdGivesADifferentIdEachTime() {
        assertThat(Ids.newId()).isNotNull().isNotEqualTo(Ids.newId());
    }

    @Test
    void typedIdKeepsItsValue() {
        UUID raw = Ids.newId();

        assertThat(new SampleId(raw).value()).isEqualTo(raw);
    }

    @Test
    void typedIdsWithTheSameValueAreEqual() {
        UUID raw = Ids.newId();

        assertThat(new SampleId(raw)).isEqualTo(new SampleId(raw));
    }

    @Test
    void typedIdRejectsNullAndNamesTheIdType() {
        assertThatNullPointerException().isThrownBy(() -> new SampleId(null)).withMessageContaining("SampleId");
    }
}

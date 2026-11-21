package edu.campus.kernel.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CampusProblemTest {

    @Test
    void carriesCodeAndDetail() {
        CampusProblem problem = new CampusProblem(ProblemCode.NOT_FOUND, "Course CS101 does not exist");

        assertThat(problem.code()).isEqualTo(ProblemCode.NOT_FOUND);
        assertThat(problem.detail()).isEqualTo("Course CS101 does not exist");
        assertThat(problem).hasMessage("Course CS101 does not exist");
    }

    @Test
    void isAnUncheckedExceptionSoItCanBeThrownAnywhere() {
        assertThatThrownBy(() -> {
                    throw new CampusProblem(ProblemCode.CONFLICT, "Already enrolled");
                })
                .isInstanceOf(RuntimeException.class)
                .isInstanceOf(CampusProblem.class);
    }

    @Test
    void keepsTheCause() {
        IllegalStateException cause = new IllegalStateException("db down");

        CampusProblem problem = new CampusProblem(ProblemCode.INTERNAL_ERROR, "Could not save", cause);

        assertThat(problem).hasCause(cause);
    }

    @Test
    void codeAndDetailAreRequired() {
        assertThatNullPointerException()
                .isThrownBy(() -> new CampusProblem(null, "x"))
                .withMessageContaining("code");
        assertThatNullPointerException()
                .isThrownBy(() -> new CampusProblem(ProblemCode.CONFLICT, null))
                .withMessageContaining("detail");
    }
}

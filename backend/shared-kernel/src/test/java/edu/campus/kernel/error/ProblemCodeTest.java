package edu.campus.kernel.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class ProblemCodeTest {

    @ParameterizedTest
    @CsvSource({
        "INTERNAL_ERROR,    500, Internal error,    https://campus.edu/problems/internal-error",
        "VALIDATION_FAILED, 400, Validation failed, https://campus.edu/problems/validation-failed",
        "NOT_FOUND,         404, Not found,         https://campus.edu/problems/not-found",
        "CONFLICT,          409, Conflict,          https://campus.edu/problems/conflict",
        "RATE_LIMITED,      429, Rate limited,      https://campus.edu/problems/rate-limited",
    })
    void eachCodeHasStatusTitleAndType(ProblemCode code, int status, String title, String type) {
        assertThat(code.status()).isEqualTo(status);
        assertThat(code.title()).isEqualTo(title);
        assertThat(code.type()).isEqualTo(URI.create(type));
    }

    @ParameterizedTest
    @EnumSource(ProblemCode.class)
    void typeIsTheKebabCaseCodeUnderTheProblemsBase(ProblemCode code) {
        String kebab = code.name().toLowerCase().replace('_', '-');

        assertThat(code.type()).hasToString("https://campus.edu/problems/" + kebab);
        assertThat(code.type().getPath()).matches("/problems/[a-z]+(-[a-z]+)*");
    }

    @Test
    void thereAreExactlyTheFiveBaseCodes() {
        assertThat(ProblemCode.values()).hasSize(5);
    }
}

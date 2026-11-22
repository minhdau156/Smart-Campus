package edu.campus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InternalPackageRuleTest {

    @ParameterizedTest
    @CsvSource({
        "edu.campus.grade,                       grade",
        "edu.campus.grade.internal,              grade",
        "edu.campus.grade.internal.jdbc,         grade",
        "edu.campus,                             ''",
    })
    void moduleIsTheFirstPackageBelowTheRoot(String pkg, String module) {
        assertThat(InternalPackageRule.moduleOf("edu.campus", pkg)).isEqualTo(module);
    }

    @ParameterizedTest
    @CsvSource({
        "edu.campus.grade,                 false",
        "edu.campus.grade.internal,        true",
        "edu.campus.grade.api.internal,    true",
        "edu.campus.grade.internals,       false",
        "edu.campus.internal,              false",
    })
    void internalMeansAnInternalSegmentInsideAModule(String pkg, boolean internal) {
        assertThat(InternalPackageRule.isInternal("edu.campus", pkg)).isEqualTo(internal);
    }
}

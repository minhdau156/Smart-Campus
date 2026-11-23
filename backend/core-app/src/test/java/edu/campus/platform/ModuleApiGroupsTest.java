package edu.campus.platform;

import static org.assertj.core.api.Assertions.assertThat;

import edu.campus.CoreApplication;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.modulith.core.ApplicationModules;

class ModuleApiGroupsTest {

    private final List<GroupedOpenApi> groups = ModuleApiGroups.groupsFor(ApplicationModules.of(CoreApplication.class));

    @Test
    void oneGroupPerModuleNamedAfterTheModule() {
        assertThat(groups).extracting(GroupedOpenApi::getGroup).containsExactly("platform");
    }

    @Test
    void aGroupShowsOnlyTheEndpointsOfItsOwnModulePackage() {
        assertThat(groups.getFirst().getPackagesToScan()).containsExactly("edu.campus.platform");
    }

    @Test
    void theSharedLibrariesGetNoGroup() {
        assertThat(groups).extracting(GroupedOpenApi::getGroup).doesNotContain("kernel", "starter");
    }
}

package io.github.counthouse.api;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    @Test
    void modulesOnlyUseEachOthersPublicApi() {
        ApplicationModules.of(ApiApplication.class).verify();
    }
}

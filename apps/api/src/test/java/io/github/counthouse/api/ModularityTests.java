package io.github.counthouse.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    @Test
    void modulesOnlyUseEachOthersPublicApi() {
        ApplicationModules.of(ApiApplication.class).verify();
    }

    /** The contract's generated types are the web layer's: services and entities keep their own. */
    @Test
    void onlyTheWebLayerUsesTheGeneratedContract() {
        noClasses()
                .that()
                .resideOutsideOfPackage("io.github.counthouse.api.web..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("io.github.counthouse.contract..")
                .check(new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("io.github.counthouse.api"));
    }
}

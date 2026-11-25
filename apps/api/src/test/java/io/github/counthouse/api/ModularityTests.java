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

    /** Generated contract types stay in a module's {@code web} package; services and entities keep their own. */
    @Test
    void onlyWebPackagesUseTheGeneratedContract() {
        noClasses()
                .that()
                .resideOutsideOfPackage("io.github.counthouse.api..web..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("io.github.counthouse.contract..")
                .because("the contract is the HTTP boundary: each module maps it to its own types in its web package")
                .check(new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("io.github.counthouse.api"));
    }
}

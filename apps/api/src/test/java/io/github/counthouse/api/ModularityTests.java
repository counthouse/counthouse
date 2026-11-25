package io.github.counthouse.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import io.github.counthouse.fixtures.contractrule.InRoot;
import io.github.counthouse.fixtures.contractrule.identity.InModule;
import io.github.counthouse.fixtures.contractrule.identity.web.InModuleWeb;
import io.github.counthouse.fixtures.contractrule.identity.web.dto.InModuleWebSubpackage;
import io.github.counthouse.fixtures.contractrule.identity.webhooks.InWebPrefixedPackage;
import io.github.counthouse.fixtures.contractrule.tenancy.internal.web.InNestedWeb;
import io.github.counthouse.fixtures.contractrule.web.InTopLevelWeb;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.DependencyDepth;

class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(ApiApplication.class);

    // Relative to surefire's working directory, apps/api.
    private static final Path DIAGRAM = Path.of("docs/modules.md");
    private static final Path GENERATED = Path.of("target/modules.md");

    @Test
    void modulesOnlyUseEachOthersPublicApi() {
        MODULES.verify();
    }

    /** Generated contract types stay in a module's {@code web} package; services and entities keep their own. */
    @Test
    void onlyWebPackagesUseTheGeneratedContract() {
        contractOnlyInModuleWebPackages("io.github.counthouse.api")
                .check(new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("io.github.counthouse.api"));
    }

    @ParameterizedTest(name = "{0} allowed: {1}")
    @MethodSource("contractRuleFixtures")
    void contractRuleAllowsOnlyAModulesWebPackage(Class<?> fixture, boolean allowed) {
        // An allowed fixture leaves the that() clause empty, which ArchUnit rejects by default.
        EvaluationResult result = contractOnlyInModuleWebPackages("io.github.counthouse.fixtures.contractrule")
                .allowEmptyShould(true)
                .evaluate(new ClassFileImporter().importClasses(fixture));
        assertThat(result.hasViolation()).as(fixture.getName()).isEqualTo(!allowed);
    }

    static Stream<Arguments> contractRuleFixtures() {
        return Stream.of(
                arguments(InModuleWeb.class, true),
                arguments(InModuleWebSubpackage.class, true),
                arguments(InNestedWeb.class, false),
                arguments(InTopLevelWeb.class, false),
                arguments(InWebPrefixedPackage.class, false),
                arguments(InModule.class, false),
                arguments(InRoot.class, false));
    }

    /** Modules are the direct subpackages of {@code root}, so {@code *.web} is exactly a module's web package. */
    static ArchRule contractOnlyInModuleWebPackages(String root) {
        return noClasses()
                .that()
                .resideOutsideOfPackage(root + ".*.web..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("io.github.counthouse.contract..")
                .because("the contract is the HTTP boundary: each module maps it to its own types in its web package");
    }

    /**
     * Spring Modulith documents modules only as PlantUML and Asciidoc, which GitHub shows as plain text, so the test
     * writes Mermaid itself. It never writes into {@code docs/}, so a CI run cannot fix the file itself.
     */
    @Test
    void moduleDiagramMatchesTheCode() throws IOException {
        String generated = moduleDiagram(MODULES);
        String committed = Files.exists(DIAGRAM) ? Files.readString(DIAGRAM) : "";
        if (!generated.equals(committed)) {
            Files.writeString(GENERATED, generated);
            fail("%s does not match the modules. In apps/api, run: cp %s %s", DIAGRAM, GENERATED, DIAGRAM);
        }
    }

    /** One node per module and one arrow per direct dependency, each sorted, so the same code gives the same bytes. */
    static String moduleDiagram(ApplicationModules modules) {
        modules.stream()
                .map(ModularityTests::identifier)
                .forEach(identifier -> assertThat(identifier)
                        .as("module identifier %s must be lowercase letters and digits, separated by dots", identifier)
                        .matches("[a-z0-9]+(\\.[a-z0-9]+)*"));
        List<ApplicationModule> sorted = modules.stream()
                .sorted(Comparator.comparing(ModularityTests::identifier))
                .toList();
        var out = new StringBuilder("""
                # API modules

                Generated from the Spring Modulith modules by `ModularityTests.moduleDiagramMatchesTheCode`. When it \
                fails, it writes the new diagram to `target/modules.md` and prints the command that copies it here.

                ```mermaid
                flowchart LR
                """);
        for (ApplicationModule module : sorted) {
            out.append("  %s[\"%s\"]\n".formatted(nodeId(module), identifier(module)));
        }
        for (ApplicationModule module : sorted) {
            module.getDependencies(modules, DependencyDepth.IMMEDIATE)
                    .uniqueModules()
                    .sorted(Comparator.comparing(ModularityTests::identifier))
                    .forEach(target -> out.append("  %s --> %s\n".formatted(nodeId(module), nodeId(target))));
        }
        return out.append("```\n").toString();
    }

    private static String identifier(ApplicationModule module) {
        return module.getIdentifier().toString();
    }

    /** Safe as a Mermaid id once the identifiers are checked: lowercase letters, digits and dots. */
    private static String nodeId(ApplicationModule module) {
        return "m_" + identifier(module).replace('.', '_');
    }
}

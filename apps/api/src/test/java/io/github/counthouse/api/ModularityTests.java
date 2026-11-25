package io.github.counthouse.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
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

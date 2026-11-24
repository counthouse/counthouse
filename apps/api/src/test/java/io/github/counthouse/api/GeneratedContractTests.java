package io.github.counthouse.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.counthouse.contract.MeApi;
import io.github.counthouse.contract.model.Me;
import io.github.counthouse.contract.model.Role;
import io.github.counthouse.contract.model.Tenant;
import io.github.counthouse.contract.model.User;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GeneratedContractTests.MeController.class)
@Import(GeneratedContractTests.MeController.class)
class GeneratedContractTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void sendsTheContractsShape() {
        assertThat(mvc.get().uri("/me").accept(MediaType.APPLICATION_JSON))
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "user": {
                            "id": "019a0000-0000-7000-8000-000000000001",
                            "role": "ADMIN"
                          },
                          "tenant": {
                            "id": "019a0000-0000-7000-8000-000000000002",
                            "name": "Example",
                            "timeZone": "America/Montevideo",
                            "locale": "es"
                          }
                        }
                        """);
    }

    @Test
    void generatedInterfacesHaveNoDefaultMethods() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isInterface();
            }
        };
        scanner.addIncludeFilter((reader, factory) -> true);
        var interfaces = new ArrayList<Class<?>>();
        for (var definition : scanner.findCandidateComponents("io.github.counthouse.contract")) {
            interfaces.add(Class.forName(definition.getBeanClassName()));
        }

        assertThat(interfaces).contains(MeApi.class);
        assertThat(interfaces)
                .flatMap(type -> List.of(type.getDeclaredMethods()))
                .filteredOn(Method::isDefault)
                .isEmpty();
    }

    // @TestComponent keeps component scanning from adding it to every @SpringBootTest context.
    @TestComponent
    @RestController
    static class MeController implements MeApi {

        @Override
        public ResponseEntity<Me> getMe() {
            var user = new User(UUID.fromString("019a0000-0000-7000-8000-000000000001"), Role.ADMIN);
            var tenant = new Tenant(
                    UUID.fromString("019a0000-0000-7000-8000-000000000002"), "Example", "America/Montevideo", "es");
            return ResponseEntity.ok(new Me(user, tenant));
        }
    }
}

package io.github.counthouse.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class TenantsMigrationTests {

    @Autowired
    JdbcClient jdbc;

    @Test
    void tenantIdsAreUuidV7() {
        UUID id = insertTenant("Demo clinic", "Europe/Berlin", "es");

        assertThat(id.version()).isEqualTo(7);
    }

    @Test
    void localeOutsideTheContractIsRejected() {
        assertRejected("Demo clinic", "Europe/Berlin", "fr", "tenants_locale_check");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    void blankNameIsRejected(String name) {
        assertRejected(name, "Europe/Berlin", "es", "tenants_name_check");
    }

    @Test
    void nameLongerThan120CharactersIsRejected() {
        assertThat(insertTenant("a".repeat(120), "Europe/Berlin", "es")).isNotNull();
        assertRejected("a".repeat(121), "Europe/Berlin", "es", "tenants_name_check");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    void blankTimeZoneIsRejected(String timeZone) {
        assertRejected("Demo clinic", timeZone, "es", "tenants_time_zone_check");
    }

    @Test
    void timeZoneLongerThan64CharactersIsRejected() {
        assertRejected("Demo clinic", "a".repeat(65), "es", "tenants_time_zone_check");
    }

    private void assertRejected(String name, String timeZone, String locale, String constraint) {
        assertThatThrownBy(() -> insertTenant(name, timeZone, locale))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    private UUID insertTenant(String name, String timeZone, String locale) {
        return jdbc.sql("""
                insert into tenants (name, time_zone, locale)
                values (?, ?, ?)
                returning id
                """).params(name, timeZone, locale).query(UUID.class).single();
    }
}

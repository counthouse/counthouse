package io.github.counthouse.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.counthouse.api.TestcontainersConfiguration.MigratorDatabase;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StaffMigrationTests {

    static final String ISSUER = "https://auth.example.test/realms/counthouse";

    @Autowired
    MigratorDatabase migrator;

    UUID tenant;
    UUID staffMember;

    @BeforeEach
    void tenantWithAStaffMember() {
        tenant = migrator.insertTenant("Clinic");
        staffMember = migrator.insertStaffMember(tenant, "STAFF", true);
    }

    @Test
    void idsAreUuidV7() {
        assertThat(staffMember.version()).isEqualTo(7);
        assertThat(migrator.insertLogin(tenant, ISSUER, "subject", staffMember).version())
                .isEqualTo(7);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void blankDisplayNameIsRejected(String name) {
        assertRejected(() -> insertStaffMember(name, "STAFF"), "staff_members_display_name_check");
    }

    @Test
    void displayNameLongerThan120CharactersIsRejected() {
        assertThat(insertStaffMember("a".repeat(120), "STAFF")).isNotNull();
        assertRejected(() -> insertStaffMember("a".repeat(121), "STAFF"), "staff_members_display_name_check");
    }

    @Test
    void roleOutsideTheContractIsRejected() {
        assertRejected(() -> insertStaffMember("Staff member", "OWNER"), "staff_members_role_check");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "auth.example.test", "https://", "https://auth example.test"})
    void issuerThatIsNotAUrlIsRejected(String issuer) {
        assertRejected(() -> migrator.insertLogin(tenant, issuer, "subject", staffMember), "logins_issuer_check");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a b"})
    void subjectWithSpacesIsRejected(String subject) {
        assertRejected(() -> migrator.insertLogin(tenant, ISSUER, subject, staffMember), "logins_subject_check");
    }

    @Test
    void subjectLongerThan255CharactersIsRejected() {
        assertRejected(
                () -> migrator.insertLogin(tenant, ISSUER, "a".repeat(256), staffMember), "logins_subject_check");
    }

    @Test
    void onlyPersonLoginsExistYet() {
        assertRejected(
                () -> migrator.asTenant(
                        tenant,
                        () -> migrator.jdbc()
                                .sql("""
                                insert into logins (tenant_id, issuer, subject, kind, staff_member_id)
                                values (?, ?, 'subject', 'DEVICE', ?)
                                """)
                                .params(tenant, ISSUER, staffMember)
                                .update()),
                "logins_kind_check");
    }

    @Test
    void aSubjectSignsInOncePerTenant() {
        migrator.insertLogin(tenant, ISSUER, "subject", staffMember);
        assertRejected(
                () -> migrator.insertLogin(tenant, ISSUER, "subject", staffMember),
                "logins_tenant_id_issuer_subject_key");

        UUID other = migrator.insertTenant("Other clinic");
        UUID otherStaffMember = migrator.insertStaffMember(other, "STAFF", true);
        assertThat(migrator.insertLogin(other, ISSUER, "subject", otherStaffMember))
                .isNotNull();
    }

    @Test
    void aLoginBelongsToAStaffMemberOfItsTenant() {
        UUID other = migrator.insertTenant("Other clinic");
        assertRejected(() -> migrator.insertLogin(other, ISSUER, "subject", staffMember), "logins_staff_member_fkey");
    }

    private UUID insertStaffMember(String name, String role) {
        return migrator.asTenant(
                tenant,
                () -> migrator.jdbc()
                        .sql("""
                        insert into staff_members (tenant_id, display_name, role)
                        values (?, ?, ?)
                        returning id
                        """)
                        .params(tenant, name, role)
                        .query(UUID.class)
                        .single());
    }

    private static void assertRejected(Runnable insert, String constraint) {
        assertThatThrownBy(insert::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }
}

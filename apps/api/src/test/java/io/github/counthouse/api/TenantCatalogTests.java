package io.github.counthouse.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Tenant isolation rules checked over the whole catalog, as the API role sees it, so a new table that breaks one fails
 * the build. Each rule also runs on the test-only schema {@code catalog_fixture}, whose tables each break one rule, to
 * show the rule can fail.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TenantCatalogTests {

    private static final String FIXTURE = "catalog_fixture";

    // How PostgreSQL prints the one policy expression allowed on a tenant table.
    private static final String TENANT_POLICY = "(tenant_id = ( SELECT current_tenant_id() AS current_tenant_id))";

    @Autowired
    JdbcClient jdbc;

    String schemas;

    @BeforeEach
    void findApplicationSchemas() {
        schemas = jdbc.sql("""
                        select string_agg(nspname, ',') from pg_namespace
                        where nspname not like 'pg\\_%' and nspname not in ('information_schema', ?)
                        """).param(FIXTURE).query(String.class).single();
    }

    @Test
    void checksCoverEveryApplicationSchema() {
        assertThat(schemas.split(",")).containsExactly("public");
    }

    @Test
    void everyTableIsIsolatedByExactlyOneTenantPolicy() {
        assertThat(isolationViolations(schemas)).isEmpty();
        assertThat(isolationViolations(FIXTURE))
                .contains(
                        "catalog_fixture.not_forced",
                        "catalog_fixture.leaky_policy",
                        "catalog_fixture.two_policies",
                        "catalog_fixture.no_tenant")
                .doesNotContain("catalog_fixture.parent");
    }

    @Test
    void tenantsAreReadableOnlyAsTheCurrentTenant() {
        List<String> policies = jdbc.sql("""
                        select permissive || ':' || cmd || ':' || array_to_string(roles, ',') || ':' || qual
                        from pg_policies
                        where schemaname = 'public' and tablename = 'tenants'
                        """).query(String.class).list();

        assertThat(policies)
                .containsExactly(
                        "PERMISSIVE:SELECT:counthouse_app:(id = ( SELECT current_tenant_id() AS current_tenant_id))");
    }

    @Test
    void everyPrimaryKeyIsOneUuidColumn() {
        // Unique checks bypass row-level security, so a guessable key could tell one tenant that
        // a value exists in another. Random UUIDs reveal nothing.
        assertThat(primaryKeyViolations(schemas)).isEmpty();
        assertThat(primaryKeyViolations(FIXTURE))
                .contains("catalog_fixture.natural_key")
                .doesNotContain("catalog_fixture.parent");
    }

    @Test
    void uniqueAndExclusionKeysOnTenantTablesIncludeTheTenant() {
        assertThat(uniqueKeyViolations(schemas)).isEmpty();
        assertThat(uniqueKeyViolations(FIXTURE))
                .contains(
                        "catalog_fixture.unique_without_tenant_label_key",
                        "catalog_fixture.unique_without_tenant_label_excl")
                .doesNotContain("catalog_fixture.parent_tenant_id_id_key");
    }

    @Test
    void foreignKeysBetweenTenantTablesIncludeTheTenant() {
        // Foreign key checks bypass row-level security, so a key without tenant_id could point
        // at another tenant's row.
        assertThat(foreignKeyViolations(schemas)).isEmpty();
        assertThat(foreignKeyViolations(FIXTURE)).containsExactly("child_without_tenant_key_parent_id_fkey");
    }

    @Test
    void apiRoleHasNoPrivilegeThatGetsAroundThePolicies() {
        // TRUNCATE ignores row-level security; REFERENCES and TRIGGER can run code with the
        // owner's rights.
        assertThat(privilegeViolations(schemas)).isEmpty();
        assertThat(privilegeViolations(FIXTURE))
                .containsExactlyInAnyOrder(
                        "catalog_fixture.risky_grants:TRUNCATE",
                        "catalog_fixture.risky_grants:REFERENCES",
                        "catalog_fixture.risky_grants:TRIGGER");
    }

    @Test
    void everyViewRunsWithTheCallersRights() {
        // A view runs with its owner's rights unless security_invoker is set, which skips the
        // policies of tables that are not forced, such as tenants. Materialized views always do.
        assertThat(viewViolations(schemas)).isEmpty();
        assertThat(viewViolations(FIXTURE))
                .containsExactlyInAnyOrder("catalog_fixture.owner_rights_view", "catalog_fixture.snapshot");
    }

    @Test
    void apiRoleCannotCreateObjectsOrTemporaryTables() {
        // A temporary table outlives a transaction on a pooled connection and comes first in the
        // search path, so it could shadow a tenant table without its policies.
        assertThat(jdbc.sql("select has_database_privilege(current_database(), 'TEMPORARY')")
                        .query(Boolean.class)
                        .single())
                .isFalse();
        assertThat(schemasWhereTheApiRoleCanCreate(schemas)).isEmpty();
        assertThat(schemasWhereTheApiRoleCanCreate(FIXTURE)).containsExactly(FIXTURE);
    }

    private List<String> isolationViolations(String schemaList) {
        return jdbc.sql("""
                        select c.oid::regclass::text
                        from pg_class c
                        join pg_namespace n on n.oid = c.relnamespace
                        where c.relkind in ('r', 'p')
                          and n.nspname = any (string_to_array(?, ','))
                          and c.oid not in ('public.tenants'::regclass, 'public.flyway_schema_history'::regclass)
                          and not (
                            c.relrowsecurity and c.relforcerowsecurity
                            and exists (
                              select from pg_attribute a
                              where a.attrelid = c.oid and a.attname = 'tenant_id' and not a.attisdropped)
                            and (select count(*) from pg_policy p where p.polrelid = c.oid) = 1
                            and exists (
                              select from pg_policies p
                              where p.schemaname = n.nspname and p.tablename = c.relname
                                and p.permissive = 'PERMISSIVE' and p.cmd = 'ALL' and p.roles = '{public}'
                                and p.qual = ? and p.with_check = ?))
                        """)
                .params(schemaList, TENANT_POLICY, TENANT_POLICY)
                .query(String.class)
                .list();
    }

    private List<String> primaryKeyViolations(String schemaList) {
        return jdbc.sql("""
                        select c.oid::regclass::text
                        from pg_class c
                        join pg_namespace n on n.oid = c.relnamespace
                        left join pg_index i on i.indrelid = c.oid and i.indisprimary
                        where c.relkind in ('r', 'p')
                          and n.nspname = any (string_to_array(?, ','))
                          and c.oid <> 'public.flyway_schema_history'::regclass
                          and not coalesce(
                            i.indnatts = 1
                              and (select a.atttypid from pg_attribute a
                                   where a.attrelid = c.oid and a.attnum = i.indkey[0]) = 'uuid'::regtype,
                            false)
                        """).param(schemaList).query(String.class).list();
    }

    private List<String> uniqueKeyViolations(String schemaList) {
        return jdbc.sql("""
                        select i.indexrelid::regclass::text
                        from pg_index i
                        join pg_class c on c.oid = i.indrelid
                        join pg_namespace n on n.oid = c.relnamespace
                        join pg_attribute t on t.attrelid = c.oid and t.attname = 'tenant_id' and not t.attisdropped
                        where (i.indisunique or i.indisexclusion) and not i.indisprimary
                          and n.nspname = any (string_to_array(?, ','))
                          and not (t.attnum = any (i.indkey))
                        """).param(schemaList).query(String.class).list();
    }

    private List<String> foreignKeyViolations(String schemaList) {
        return jdbc.sql("""
                        select con.conname
                        from pg_constraint con
                        join pg_namespace n on n.oid = con.connamespace
                        join pg_attribute src on src.attrelid = con.conrelid and src.attname = 'tenant_id'
                        join pg_attribute dst on dst.attrelid = con.confrelid and dst.attname = 'tenant_id'
                        where con.contype = 'f'
                          and n.nspname = any (string_to_array(?, ','))
                          and not (src.attnum = any (con.conkey) and dst.attnum = any (con.confkey))
                        """).param(schemaList).query(String.class).list();
    }

    private List<String> privilegeViolations(String schemaList) {
        return jdbc.sql("""
                        select c.oid::regclass::text || ':' || p.privilege
                        from pg_class c
                        join pg_namespace n on n.oid = c.relnamespace
                        cross join unnest(array['TRUNCATE', 'REFERENCES', 'TRIGGER']) as p(privilege)
                        where c.relkind in ('r', 'p', 'v', 'm')
                          and n.nspname = any (string_to_array(?, ','))
                          and has_table_privilege(current_user, c.oid, p.privilege)
                        """).param(schemaList).query(String.class).list();
    }

    private List<String> viewViolations(String schemaList) {
        return jdbc.sql("""
                        select c.oid::regclass::text
                        from pg_class c
                        join pg_namespace n on n.oid = c.relnamespace
                        where c.relkind in ('v', 'm')
                          and n.nspname = any (string_to_array(?, ','))
                          and (c.relkind = 'm' or not coalesce(c.reloptions @> array['security_invoker=true'], false))
                        """).param(schemaList).query(String.class).list();
    }

    private List<String> schemasWhereTheApiRoleCanCreate(String schemaList) {
        return jdbc.sql("""
                        select nspname from pg_namespace
                        where nspname = any (string_to_array(?, ','))
                          and has_schema_privilege(current_user, oid, 'CREATE')
                        """).param(schemaList).query(String.class).list();
    }
}

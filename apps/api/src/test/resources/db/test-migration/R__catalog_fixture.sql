-- Test-only schema of tables that each break one tenant isolation rule.
-- TenantCatalogTests runs every rule on this schema and expects it to name
-- each table, so a rule that can no longer fail is caught. The rules on the
-- real schemas exclude this one.
create schema catalog_fixture;

-- Breaks: not forced.
create table catalog_fixture.not_forced (
    id        uuid primary key,
    tenant_id uuid not null references tenants (id)
);
alter table catalog_fixture.not_forced enable row level security;
create policy not_forced_own_tenant on catalog_fixture.not_forced
    using (tenant_id = (select current_tenant_id()))
    with check (tenant_id = (select current_tenant_id()));

-- Breaks: the policy lets every row through.
create table catalog_fixture.leaky_policy (
    id        uuid primary key,
    tenant_id uuid not null references tenants (id)
);
alter table catalog_fixture.leaky_policy enable row level security;
alter table catalog_fixture.leaky_policy force row level security;
create policy leaky_policy_own_tenant on catalog_fixture.leaky_policy
    using (tenant_id = (select current_tenant_id()) or true)
    with check (tenant_id = (select current_tenant_id()));

-- Breaks: a second permissive policy is OR'd with the first.
create table catalog_fixture.two_policies (
    id        uuid primary key,
    tenant_id uuid not null references tenants (id)
);
alter table catalog_fixture.two_policies enable row level security;
alter table catalog_fixture.two_policies force row level security;
create policy two_policies_own_tenant on catalog_fixture.two_policies
    using (tenant_id = (select current_tenant_id()))
    with check (tenant_id = (select current_tenant_id()));
create policy two_policies_reports on catalog_fixture.two_policies
    for select
    using (true);

-- Breaks: no tenant_id column.
create table catalog_fixture.no_tenant (
    id uuid primary key
);
alter table catalog_fixture.no_tenant enable row level security;
alter table catalog_fixture.no_tenant force row level security;

-- Breaks: a natural primary key, which a unique check could reveal across tenants.
create table catalog_fixture.natural_key (
    code      text primary key,
    tenant_id uuid not null references tenants (id)
);

-- Breaks: a unique key and an exclusion constraint without tenant_id.
create table catalog_fixture.unique_without_tenant (
    id        uuid primary key,
    tenant_id uuid not null references tenants (id),
    label     text not null,
    constraint unique_without_tenant_label_key unique (label),
    constraint unique_without_tenant_label_excl exclude using gist (label with =)
);

-- Breaks nothing: the control that every rule must accept.
create table catalog_fixture.parent (
    id        uuid primary key,
    tenant_id uuid not null references tenants (id),
    constraint parent_tenant_id_id_key unique (tenant_id, id)
);
alter table catalog_fixture.parent enable row level security;
alter table catalog_fixture.parent force row level security;
create policy parent_own_tenant on catalog_fixture.parent
    using (tenant_id = (select current_tenant_id()))
    with check (tenant_id = (select current_tenant_id()));

-- Breaks: a foreign key between tenant tables without tenant_id.
create table catalog_fixture.child_without_tenant_key (
    id        uuid primary key,
    tenant_id uuid not null references tenants (id),
    parent_id uuid not null references catalog_fixture.parent (id)
);

-- Breaks: privileges that skip or get around the policies.
create table catalog_fixture.risky_grants (
    id        uuid primary key,
    tenant_id uuid not null references tenants (id)
);
grant truncate, references, trigger on catalog_fixture.risky_grants to counthouse_app;

-- Breaks: views that run with their owner's rights.
create view catalog_fixture.owner_rights_view as select id from catalog_fixture.parent;
create materialized view catalog_fixture.snapshot as select id from catalog_fixture.parent with no data;

-- Breaks: the API role may create objects here.
grant create on schema catalog_fixture to counthouse_app;

-- Test-only tenant-owned tables: prove forced row-level security and the
-- catalog rules before the first real tenant-owned table exists.
create table rls_probe (
    id        uuid primary key default uuidv7(),
    tenant_id uuid not null references tenants (id),
    label     text not null,
    constraint rls_probe_tenant_id_id_key unique (tenant_id, id)
);

alter table rls_probe enable row level security;
alter table rls_probe force row level security;

create policy rls_probe_own_tenant on rls_probe
    using (tenant_id = (select current_tenant_id()))
    with check (tenant_id = (select current_tenant_id()));

grant select, insert, update on rls_probe to counthouse_app;

create table rls_probe_child (
    id        uuid primary key default uuidv7(),
    tenant_id uuid not null references tenants (id),
    probe_id  uuid not null,
    constraint rls_probe_child_probe_fkey foreign key (tenant_id, probe_id) references rls_probe (tenant_id, id)
);

alter table rls_probe_child enable row level security;
alter table rls_probe_child force row level security;

create policy rls_probe_child_own_tenant on rls_probe_child
    using (tenant_id = (select current_tenant_id()))
    with check (tenant_id = (select current_tenant_id()));

grant select, insert on rls_probe_child to counthouse_app;

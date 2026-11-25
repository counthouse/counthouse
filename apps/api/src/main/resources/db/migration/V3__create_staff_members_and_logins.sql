-- A person who works for a tenant. The API reads it on every request to
-- know who is calling and with which role.
create table staff_members (
    id           uuid primary key default uuidv7(),
    tenant_id    uuid not null references tenants (id),
    display_name text not null
        constraint staff_members_display_name_check check (display_name ~ '\S' and length(display_name) <= 120),
    role         text not null
        constraint staff_members_role_check check (role in ('STAFF', 'ADMIN')),
    active       boolean not null default true,
    constraint staff_members_tenant_id_id_key unique (tenant_id, id)
);

alter table staff_members enable row level security;
alter table staff_members force row level security;

create policy staff_members_own_tenant on staff_members
    using (tenant_id = (select current_tenant_id()))
    with check (tenant_id = (select current_tenant_id()));

grant select on staff_members to counthouse_app;

-- A Keycloak user that signs in as a staff member, identified as OpenID
-- Connect does: by the token's issuer and subject.
create table logins (
    id              uuid primary key default uuidv7(),
    tenant_id       uuid not null references tenants (id),
    issuer          text not null
        constraint logins_issuer_check check (issuer ~ '^https?://\S+$' and length(issuer) <= 255),
    subject         text not null
        constraint logins_subject_check check (subject ~ '^\S+$' and length(subject) <= 255),
    kind            text not null
        constraint logins_kind_check check (kind in ('PERSON')),
    staff_member_id uuid not null,
    constraint logins_tenant_id_issuer_subject_key unique (tenant_id, issuer, subject),
    constraint logins_staff_member_fkey foreign key (tenant_id, staff_member_id)
        references staff_members (tenant_id, id)
);

alter table logins enable row level security;
alter table logins force row level security;

create policy logins_own_tenant on logins
    using (tenant_id = (select current_tenant_id()))
    with check (tenant_id = (select current_tenant_id()));

grant select on logins to counthouse_app;

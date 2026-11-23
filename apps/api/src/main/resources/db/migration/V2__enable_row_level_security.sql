-- For the double-booking exclusion constraint (uuid equality inside a gist index).
create extension if not exists btree_gist;

-- The tenant of the current transaction, set by the API with
-- set_config('app.tenant_id', ?, true). Raises when it is missing, so every
-- policy that calls it fails closed.
create function current_tenant_id() returns uuid
    language plpgsql
    stable
as $$
declare
    tenant text := current_setting('app.tenant_id', true);
begin
    if tenant is null or tenant = '' then
        raise exception 'app.tenant_id is not set';
    end if;
    return tenant::uuid;
end
$$;

-- Not forced: the migrator creates tenants (the provisioning path).
-- The API reads its own tenant only.
alter table tenants enable row level security;

create policy tenants_own_tenant on tenants
    for select
    to counthouse_app
    using (id = (select current_tenant_id()));

grant select on tenants to counthouse_app;

-- PUBLIC may create temporary tables by default. A temporary table outlives a
-- transaction on a pooled connection and comes first in the search path, so it
-- could shadow a tenant table without its policies.
do $$
begin
    execute format('revoke temporary on database %I from public', current_database());
end
$$;

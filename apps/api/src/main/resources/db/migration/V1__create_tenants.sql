create table tenants (
    id        uuid primary key default uuidv7(),
    name      text not null
        constraint tenants_name_check check (btrim(name) <> '' and length(name) <= 120),
    time_zone text not null
        constraint tenants_time_zone_check check (btrim(time_zone) <> '' and length(time_zone) <= 64),
    locale    text not null
        constraint tenants_locale_check check (locale in ('es', 'en'))
);

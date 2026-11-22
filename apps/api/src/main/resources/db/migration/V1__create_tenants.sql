create table tenants (
    id        uuid primary key default uuidv7(),
    name      text not null
        constraint tenants_name_check check (name ~ '\S' and length(name) <= 120),
    time_zone text not null
        constraint tenants_time_zone_check check (time_zone ~ '\S' and length(time_zone) <= 64),
    locale    text not null
        constraint tenants_locale_check check (locale in ('es', 'en'))
);

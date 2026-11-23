-- Test-only roles. Each environment creates its own, with its own passwords.
-- The migrator owns the database and runs Flyway; the API connects as counthouse_app.
create role counthouse_migrator login password 'migrator';
create role counthouse_app login password 'app';
alter database counthouse owner to counthouse_migrator;

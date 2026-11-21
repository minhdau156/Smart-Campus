#!/bin/sh
# Runs ONCE, when the Postgres volume is created for the first time (Postgres image feature:
# every file in /docker-entrypoint-initdb.d/ runs on first start). To run it again:
#   docker compose -f infra/docker-compose.yml down -v   (deletes all local data!)
#
# It is a shell script, not a .sql file, so that the passwords come from infra/.env
# (passed in as environment variables) instead of being written here a second time.
set -eu

# 1. Users, schemas and privileges in the app database ("campus").
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	-- Nobody gets rights by default; each user gets only what is granted below.
	REVOKE ALL ON DATABASE "$POSTGRES_DB" FROM PUBLIC;
	REVOKE ALL ON SCHEMA public FROM PUBLIC;

	-- One user per app. core_app is the modular monolith; the two services were split out.
	CREATE ROLE core_app         LOGIN PASSWORD '$CORE_APP_DB_PASSWORD';
	CREATE ROLE registration_svc LOGIN PASSWORD '$REGISTRATION_SVC_DB_PASSWORD';
	CREATE ROLE notification_svc LOGIN PASSWORD '$NOTIFICATION_SVC_DB_PASSWORD';
	GRANT CONNECT ON DATABASE "$POSTGRES_DB" TO core_app, registration_svc, notification_svc;

	-- One schema per module. core_app owns all of them except the two split-out services' schemas.
	CREATE SCHEMA identity     AUTHORIZATION core_app;
	CREATE SCHEMA course       AUTHORIZATION core_app;
	CREATE SCHEMA timetable    AUTHORIZATION core_app;
	CREATE SCHEMA grade        AUTHORIZATION core_app;
	CREATE SCHEMA announcement AUTHORIZATION core_app;
	CREATE SCHEMA club         AUTHORIZATION core_app;
	CREATE SCHEMA marketplace  AUTHORIZATION core_app;
	CREATE SCHEMA lostfound    AUTHORIZATION core_app;
	CREATE SCHEMA payment      AUTHORIZATION core_app;
	CREATE SCHEMA platform     AUTHORIZATION core_app;

	-- Each service owns ONLY its own schema (spec D3: a wrong-schema query fails at runtime).
	CREATE SCHEMA registration AUTHORIZATION registration_svc;
	CREATE SCHEMA notification AUTHORIZATION notification_svc;

	-- Unqualified table names resolve to the user's own schema.
	ALTER ROLE registration_svc SET search_path = registration;
	ALTER ROLE notification_svc SET search_path = notification;
EOSQL

# 2. A separate database for Keycloak, so its tables never mix with ours.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE ROLE keycloak LOGIN PASSWORD '$KEYCLOAK_DB_PASSWORD';
	CREATE DATABASE keycloak OWNER keycloak;
	REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
EOSQL

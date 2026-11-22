-- Baseline for the platform schema. It creates no tables yet (the outbox comes in T7).
-- Running it makes Flyway create platform.flyway_schema_history, which proves the setup works.
COMMENT ON SCHEMA platform IS 'core-app platform module: shared technical tables (outbox, ...)';

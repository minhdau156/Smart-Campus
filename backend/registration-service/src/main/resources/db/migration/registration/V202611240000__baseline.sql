-- Baseline for the registration schema. It creates no business tables yet (they come with the
-- registration module). Running it makes Flyway create registration.flyway_schema_history,
-- which proves the setup works as the restricted user registration_svc.
COMMENT ON SCHEMA registration IS 'registration-service: seat requests, enrollments, waitlists';

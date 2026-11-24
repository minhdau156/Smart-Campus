-- Baseline for the notification schema. It creates no business tables yet (they come with the
-- notification module). Running it makes Flyway create notification.flyway_schema_history,
-- which proves the setup works as the restricted user notification_svc.
COMMENT ON SCHEMA notification IS 'notification-service: inbox, preferences, delivery state';

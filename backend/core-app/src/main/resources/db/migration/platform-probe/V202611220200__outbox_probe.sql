-- Dev/test only (T7 outbox spike): the "business row" saved together with the probe event.
-- This folder is added to spring.flyway.locations only by the dev and test profiles,
-- so a production database never gets this table.

CREATE TABLE outbox_probe
(
    id         UUID                     NOT NULL PRIMARY KEY,
    note       TEXT                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

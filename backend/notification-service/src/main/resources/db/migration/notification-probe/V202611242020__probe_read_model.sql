-- Dev/test only (loaded by the dev and test profiles, never in production).
-- ProbeListener writes one row per probe event it receives from core-app (platform.probe).
-- The row has its OWN id on purpose: nothing here stops a second row for the same event, so only
-- the dedupe in processed_events keeps it to one (that is what the tests prove, spec O4).

CREATE TABLE probe_read_model
(
    id         UUID                     NOT NULL DEFAULT gen_random_uuid(),
    event_id   UUID                     NOT NULL, -- the envelope's eventId
    probe_id   UUID                     NOT NULL, -- the probe's id (core-app's outbox_probe.id)
    note       TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);

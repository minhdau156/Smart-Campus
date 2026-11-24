-- Consumer dedupe (spec D2). One row per event that a listener of this service has handled.
-- platform-starter's IdempotentConsumer inserts the row in the SAME transaction as the listener's
-- work: if the row already exists, the event is a repeat and the work is skipped.
-- The key is (consumer, event_id): two different listeners may each handle the same event once.

CREATE TABLE processed_events
(
    consumer     TEXT                     NOT NULL, -- listener name, e.g. notification.probe
    event_id     UUID                     NOT NULL, -- the envelope's eventId
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);

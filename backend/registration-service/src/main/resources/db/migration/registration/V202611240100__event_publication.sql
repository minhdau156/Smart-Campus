-- Outbox table of the Spring Modulith Event Publication Registry (spec D1), in this service's own
-- schema. Same table as core-app's platform.event_publication (see that migration for details).
-- Copied from the Modulith 2.1.3 jar (org/springframework/modulith/events/jdbc/schemas/v2/
-- schema-postgresql.sql), because automatic schema creation is off: Flyway owns all tables.
-- The registry finds the table through spring.modulith.events.jdbc.schema = the Flyway default
-- schema (set in platform-starter), here `registration`.

CREATE TABLE event_publication
(
    id                     UUID                     NOT NULL,
    listener_id            TEXT                     NOT NULL, -- who handles it (the Kafka externalizer)
    event_type             TEXT                     NOT NULL, -- Java class of the event
    serialized_event       TEXT                     NOT NULL, -- the event as JSON
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,          -- NULL = not delivered yet
    status                 TEXT,                              -- PUBLISHED, PROCESSING, COMPLETED, FAILED, RESUBMITTED
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);

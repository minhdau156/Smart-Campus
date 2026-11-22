-- Outbox table of the Spring Modulith Event Publication Registry (spec D1).
-- Every event that must reach Kafka is written here in the SAME transaction as the business data.
-- After the commit it is sent to Kafka; completion_date is set only when Kafka has acknowledged it.
--
-- Copied from the Modulith 2.1.3 jar (org/springframework/modulith/events/jdbc/schemas/v2/
-- schema-postgresql.sql), because automatic schema creation is off: Flyway owns all tables.
-- The registry finds the table through spring.modulith.events.jdbc.schema=platform.

CREATE TABLE event_publication
(
    id                     UUID                     NOT NULL,
    listener_id            TEXT                     NOT NULL, -- who handles it (here: the Kafka externalizer)
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

package edu.campus.kernel.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The event's JSON shape is a contract between services (spec D4). The golden file is the agreed
 * shape: if this test fails, the change is breaking and needs a spec change first.
 */
class DomainEventJsonTest {

    record EnrollmentConfirmed(String studentId, String sectionId) {}

    private static final DomainEvent<EnrollmentConfirmed> EXPECTED = new DomainEvent<>(
            UUID.fromString("0b9f6c2e-4d1a-4c3b-9a57-2f1e8d7c6b5a"),
            "EnrollmentConfirmed",
            1,
            Instant.parse("2026-09-07T08:00:00Z"),
            "section-1234",
            new EnrollmentConfirmed("student-42", "section-1234"));

    private final JsonMapper mapper = JsonMapper.builder().build();

    private final EventTypes types = new EventTypes().register("EnrollmentConfirmed", EnrollmentConfirmed.class);

    private static String golden() throws IOException {
        try (InputStream in = DomainEventJsonTest.class.getResourceAsStream("/golden/domain-event.json")) {
            assertThat(in).as("golden file").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void serializesToTheGoldenShape() throws IOException {
        JsonNode written = mapper.readTree(mapper.writeValueAsString(EXPECTED));

        assertThat(written).isEqualTo(mapper.readTree(golden()));
    }

    @Test
    void occurredAtIsIso8601Utc() {
        JsonNode written = mapper.valueToTree(EXPECTED);

        assertThat(written.get("occurredAt").isString()).isTrue();
        assertThat(written.get("occurredAt").asString()).isEqualTo("2026-09-07T08:00:00Z");
    }

    @Test
    void goldenFileReadsBackUsingTheTypeRegistry() throws IOException {
        String json = golden();
        Class<?> payloadClass = types.resolve(mapper.readTree(json).get("type").asString());
        JavaType eventType = mapper.getTypeFactory().constructParametricType(DomainEvent.class, payloadClass);

        DomainEvent<?> read = mapper.readValue(json, eventType);

        assertThat(read).isEqualTo(EXPECTED);
        assertThat(read.payload()).isInstanceOf(EnrollmentConfirmed.class);
    }

    @Test
    void roundTripKeepsTheEventEqual() {
        JavaType eventType =
                mapper.getTypeFactory().constructParametricType(DomainEvent.class, EnrollmentConfirmed.class);

        DomainEvent<?> back = mapper.readValue(mapper.writeValueAsString(EXPECTED), eventType);

        assertThat(back).isEqualTo(EXPECTED);
    }
}

package edu.campus.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * The consumer registry {@code infra/consumer-registry.txt} (spec D5) must list every Kafka
 * consumer of this service, because {@code infra/replay.sh} reads it to decide whether a consumer
 * may be replayed. A consumer missing from the file could not be replayed at all, and a wrong
 * "replayable" could re-send emails.
 */
class ConsumerRegistryTest {

    /** Surefire runs in backend/notification-service, so the repo root is two folders up. */
    private static final Path REGISTRY = Path.of("../../infra/consumer-registry.txt");

    record Entry(String group, String schema, String replay) {}

    @Test
    void everyKafkaListenerOfThisServiceIsRegisteredWithItsSchema() throws Exception {
        Map<String, Entry> registry = readRegistry();

        for (String group : kafkaListenerGroups()) {
            assertThat(registry).as("consumer-registry.txt entry for " + group).containsKey(group);
            assertThat(registry.get(group).schema()).as(group).isEqualTo("notification");
        }
    }

    @Test
    void theProbeConsumerIsReplayable() throws Exception {
        // It only writes a read model (probe_read_model), so rebuilding it is safe.
        assertThat(readRegistry().get(ProbeListener.CONSUMER).replay()).isEqualTo("replayable");
    }

    @Test
    void everyEntryIsWellFormed() throws Exception {
        for (Entry entry : readRegistry().values()) {
            assertThat(entry.replay()).as(entry.group()).isIn("replayable", "non-replayable");
            assertThat(entry.schema()).as(entry.group()).matches("[a-z][a-z0-9_]*");
        }
    }

    /** Lines "<group> <schema> <replayable|non-replayable>"; '#' starts a comment. */
    private static Map<String, Entry> readRegistry() throws IOException {
        return Files.readAllLines(REGISTRY).stream()
                .map(line -> line.replaceFirst("#.*", "").strip())
                .filter(line -> !line.isEmpty())
                .map(line -> line.split("\\s+"))
                .peek(parts -> assertThat(parts).as(String.join(" ", parts)).hasSize(3))
                .map(parts -> new Entry(parts[0], parts[1], parts[2]))
                .collect(Collectors.toMap(Entry::group, Function.identity()));
    }

    /**
     * The group of every @KafkaListener method in this service. It reads the class files directly
     * (not Spring's component scan), so listeners behind a {@code @Profile} are found too.
     */
    private static List<String> kafkaListenerGroups() throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        var readers = new CachingMetadataReaderFactory(resolver);
        List<String> groups = new ArrayList<>();
        for (Resource classFile : resolver.getResources("classpath*:edu/campus/notification/**/*.class")) {
            var type = readers.getMetadataReader(classFile).getAnnotationMetadata();
            for (MethodMetadata method : type.getAnnotatedMethods(KafkaListener.class.getName())) {
                Map<String, Object> listener = method.getAnnotationAttributes(KafkaListener.class.getName());
                String groupId = (String) listener.get("groupId");
                groups.add(groupId.isEmpty() ? (String) listener.get("id") : groupId);
            }
        }
        assertThat(groups).as("the scan finds the probe listener").contains(ProbeListener.CONSUMER);
        return groups;
    }
}

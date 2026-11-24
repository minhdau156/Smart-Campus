package edu.campus.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Spec D3 / I2: notification-service can read and write ONLY the {@code notification} schema.
 *
 * <p>The database is set up by the same init script as Compose (see
 * {@link TestcontainersConfiguration}), and the app's {@link JdbcTemplate} is logged in as
 * {@code notification_svc}. To make sure a failure means "not allowed" and not "no such table",
 * the container's superuser first creates a real table in every other schema. The expected error is
 * PostgreSQL's SQLState {@code 42501} ("insufficient privilege").
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SchemaIsolationIT {

    /** PostgreSQL's code for "permission denied". */
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    /** Every schema from infra/postgres/init except notification itself. */
    private static final List<String> OTHER_SCHEMAS = List.of(
            "identity",
            "course",
            "timetable",
            "grade",
            "announcement",
            "club",
            "marketplace",
            "lostfound",
            "payment",
            "platform",
            "registration");

    /** The app's own connection pool: logged in as notification_svc. */
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PostgreSQLContainer postgres;

    @BeforeEach
    void everyOtherSchemaHasATableWithARow() throws SQLException {
        try (Connection superuser = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                Statement sql = superuser.createStatement()) {
            for (String schema : OTHER_SCHEMAS) {
                sql.execute("CREATE TABLE IF NOT EXISTS %s.isolation_probe (id int)".formatted(schema));
                sql.execute("INSERT INTO %s.isolation_probe VALUES (1)".formatted(schema));
            }
        }
    }

    @Test
    void theAppIsLoggedInAsNotificationSvc() {
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("notification_svc");
    }

    @Test
    void itCanReadAndWriteItsOwnSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS notification.isolation_own (id int)");
        jdbc.update("INSERT INTO notification.isolation_own VALUES (1)");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification.isolation_own", Integer.class))
                .isPositive();
    }

    @ParameterizedTest
    @ValueSource(strings = {"registration", "course"})
    void selectOnTheNamedOtherSchemasIsDenied(String schema) {
        assertPermissionDenied("SELECT * FROM %s.isolation_probe".formatted(schema));
    }

    @Test
    void selectOnEveryOtherSchemaIsDenied() {
        for (String schema : OTHER_SCHEMAS) {
            assertPermissionDenied("SELECT * FROM %s.isolation_probe".formatted(schema));
        }
    }

    @Test
    void writesToOtherSchemasAreDenied() {
        for (String schema : OTHER_SCHEMAS) {
            assertPermissionDenied("INSERT INTO %s.isolation_probe VALUES (2)".formatted(schema));
            assertPermissionDenied("CREATE TABLE %s.intruder (id int)".formatted(schema));
        }
    }

    /** The init script also takes the default rights on `public` away from everyone. */
    @Test
    void creatingTablesInPublicIsDenied() {
        assertPermissionDenied("CREATE TABLE public.intruder (id int)");
    }

    private void assertPermissionDenied(String statement) {
        assertThatThrownBy(() -> jdbc.execute(statement))
                .as(statement)
                .satisfies(error -> assertThat(NestedExceptionUtils.getMostSpecificCause(error))
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                sql -> assertThat(sql.getSQLState())
                                        .as("SQLState of: " + sql.getMessage())
                                        .isEqualTo(INSUFFICIENT_PRIVILEGE)));
    }
}

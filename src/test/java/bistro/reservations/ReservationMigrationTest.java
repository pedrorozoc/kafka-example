package bistro.reservations;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationMigrationTest {
    private final String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            ReservationTestDatabase.databaseUrl(),
            ReservationTestDatabase.databaseUsername(),
            ReservationTestDatabase.databasePassword());
    private final JdbcTemplate sql = new JdbcTemplate(dataSource);

    @BeforeEach
    void migrateToV3() {
        flyway("3", "America/Bogota").migrate();
        dataSource.setSchema(schema);
        sql.update("INSERT INTO dining_table (id, capacity) VALUES (1, 13)");
    }

    @AfterEach
    void dropTestSchema() {
        dataSource.setSchema(null);
        sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    @Test
    void migratesBothHistoricalConfirmationsToTheSameLocalMinute() {
        UUID first = legacyReservation("2030-06-13T00:00:10Z", "4", "");
        UUID second = legacyReservation("2030-06-13T00:00:45.123456Z", "13", "7");

        flyway("4", "America/Bogota").migrate();

        var localMinute = LocalDateTime.parse("2030-06-12T19:00");
        assertThat(sql.query("""
                SELECT reservation_code, reservation_time, party_size, status, assigned_table_id
                FROM reservation
                """, (row, number) -> new MigratedReservation(
                row.getObject("reservation_code", UUID.class), row.getObject("reservation_time", LocalDateTime.class),
                row.getInt("party_size"), row.getString("status"), row.getLong("assigned_table_id"))))
                .containsExactlyInAnyOrder(new MigratedReservation(first, localMinute, 4, "CONFIRMED", 1L),
                        new MigratedReservation(second, localMinute, 13, "CONFIRMED", 1L));
        assertThat(columnType("reservation_time")).isEqualTo("timestamp without time zone");
        assertThat(columnType("party_size")).isEqualTo("integer");
        assertThat(sql.queryForList(columnTypeQuery(), String.class, schema, "reservation_time_remainder")).isEmpty();
        assertThat(sql.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = ? AND indexname = 'reservation_occupied_table_time_idx'
                """, String.class, schema)).contains("(assigned_table_id, reservation_time)", "WHERE").doesNotContain("UNIQUE");
    }

    @Test
    void backfillsTableNumbersAndPreservesHistoricalAssignments() {
        UUID reservationCode = legacyReservation("2030-06-13T00:00:10Z", "4", "");
        flyway("4", "America/Bogota").migrate();

        flyway("5", "America/Bogota").migrate();

        assertThat(sql.queryForObject("""
                SELECT t.table_number FROM reservation r
                JOIN dining_table t ON t.id = r.assigned_table_id
                WHERE r.reservation_code = ?
                """, String.class, reservationCode)).isEqualTo("1");
        sql.update("INSERT INTO dining_table (id, table_number, capacity) VALUES (2, 'T-02', 4)");
        assertThat(sql.queryForObject("SELECT table_number FROM dining_table WHERE id = 2", String.class))
                .isEqualTo("T-02");
        assertThatThrownBy(() -> sql.update("INSERT INTO dining_table (id, capacity) VALUES (3, 4)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "2030-06-13T00:00:10Z, 2147483648, America/Bogota, cantidad",
            "infinity, 4, America/Bogota, fecha",
            "2030-06-13T00:00:10Z, 4, Invalid/Zone, zona"
    })
    void rollsBackIfLegacyDataOrZoneCannotBeConverted(String time, String size, String zone, String diagnostic) {
        legacyReservation(time, size, "7");
        var before = sql.queryForList("SELECT * FROM reservation");

        assertThatThrownBy(() -> flyway("4", zone).migrate())
                .isInstanceOf(FlywayException.class).hasMessageContaining(diagnostic);

        assertThat(sql.queryForList("SELECT * FROM reservation")).isEqualTo(before);
        assertThat(columnType("reservation_time")).isEqualTo("timestamp with time zone");
        assertThat(columnType("party_size")).isEqualTo("numeric");
        assertThat(columnType("reservation_time_remainder")).isEqualTo("text");
        assertThat(flyway("4", "America/Bogota").info().current().getVersion().getVersion()).isEqualTo("3");
    }

    private Flyway flyway(String target, String zone) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).target(target)
                .initSql("SET TIME ZONE 'UTC'")
                .placeholders(Map.of("restaurant_zone", zone)).load();
    }

    private UUID legacyReservation(String time, String size, String remainder) {
        UUID code = UUID.randomUUID();
        sql.update("""
                INSERT INTO reservation (
                    reservation_code, customer_name, customer_email, reservation_time,
                    party_size, status, assigned_table_id, reservation_time_remainder
                ) VALUES (?, 'Ana Pérez', 'ana@example.org', CAST(? AS timestamptz),
                    CAST(? AS numeric), 'CONFIRMED', 1, ?)
                """, code, time, size, remainder);
        return code;
    }

    private String columnType(String column) {
        return sql.queryForObject(columnTypeQuery(), String.class, schema, column);
    }

    private String columnTypeQuery() {
        return """
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = ? AND table_name = 'reservation' AND column_name = ?
                """;
    }

    private record MigratedReservation(UUID code, LocalDateTime time, int size, String status, long tableId) {}
}

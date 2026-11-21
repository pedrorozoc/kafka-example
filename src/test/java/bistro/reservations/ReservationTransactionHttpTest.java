package bistro.reservations;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.SqlParameterValue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationTransactionHttpTest extends ReservationTestDatabase {
    @Test
    void waitsForCandidateLockAndReadsTheFirstDecisionAfterCommit() throws Exception {
        table(1, 4);
        sql.execute("""
                CREATE FUNCTION hold_reservation_insert() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    PERFORM pg_advisory_xact_lock(51745);
                    RETURN NEW;
                END;
                $$
                """);
        sql.execute("""
                CREATE TRIGGER hold_reservation_insert BEFORE INSERT ON reservation
                FOR EACH ROW EXECUTE FUNCTION hold_reservation_insert()
                """);

        CompletableFuture<Created> first = null;
        CompletableFuture<Created> second = null;
        try {
            try (Connection gate = dataSource.getConnection()) {
                execute(gate, "SELECT pg_advisory_lock(51745)");
                try {
                    first = CompletableFuture.supplyAsync(() -> created(START, "4"));
                    await(() -> hasBlockedSession("advisory", "%insert into reservation%"));

                    second = CompletableFuture.supplyAsync(() -> created(START, "4"));
                    await(() -> hasBlockedSession(null, "%for update%"));
                    assertThat(second.isDone()).isFalse();
                } finally {
                    execute(gate, "SELECT pg_advisory_unlock(51745)");
                }
            }

            Created confirmed = first.get(10, TimeUnit.SECONDS);
            Created rejected = second.get(10, TimeUnit.SECONDS);
            assertThat(confirmed.status()).isEqualTo("CONFIRMED");
            assertThat(rejected.status()).isEqualTo("REJECTED");
            assertThat(confirmed.code()).isNotEqualTo(rejected.code());
            assertThat(reservations()).hasSize(2).anySatisfy(row -> {
                assertThat(row.code()).isEqualTo(confirmed.code());
                assertThat(row.tableId()).isEqualTo(1L);
            }).anySatisfy(row -> {
                assertThat(row.code()).isEqualTo(rejected.code());
                assertThat(row.tableId()).isNull();
            });
        } finally {
            awaitCompletion(first);
            awaitCompletion(second);
            sql.execute("DROP TRIGGER hold_reservation_insert ON reservation");
            sql.execute("DROP FUNCTION hold_reservation_insert()");
        }
    }

    @Test
    void rollsBackDatabaseFailureAndReleasesTheTableForTheNextRequest() {
        table(1, 4);
        sql.execute("""
                CREATE FUNCTION fail_reservation_insert() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'Fallo de escritura simulado';
                END;
                $$
                """);
        sql.execute("""
                CREATE TRIGGER fail_reservation_insert AFTER INSERT ON reservation
                FOR EACH ROW EXECUTE FUNCTION fail_reservation_insert()
                """);
        try {
            problem(post(request(START, "4")), 500, "Error del sistema", "No se pudo completar la operación.");
            assertThat(reservations()).isEmpty();
        } finally {
            sql.execute("DROP TRIGGER fail_reservation_insert ON reservation");
            sql.execute("DROP FUNCTION fail_reservation_insert()");
        }
        assertThat(created(START, "4").status()).isEqualTo("CONFIRMED");
        assertThat(reservations()).hasSize(1).allSatisfy(row -> assertThat(row.tableId()).isEqualTo(1L));
    }

    private boolean hasBlockedSession(String waitEvent, String queryPattern) {
        return Boolean.TRUE.equals(sql.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM pg_stat_activity
                    WHERE wait_event_type = 'Lock'
                      AND (? IS NULL OR wait_event = ?)
                      AND query ILIKE ?
                      AND cardinality(pg_blocking_pids(pid)) > 0
                )
                """, Boolean.class, new SqlParameterValue(Types.VARCHAR, waitEvent), waitEvent, queryPattern));
    }

    private void await(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        assertThat(condition.getAsBoolean()).as("espera por bloqueo visible en PostgreSQL").isTrue();
    }

    private void awaitCompletion(CompletableFuture<Created> request) throws Exception {
        if (request != null) {
            request.handle((result, failure) -> null).get(10, TimeUnit.SECONDS);
        }
    }

    private void execute(Connection connection, String statement) throws SQLException {
        try (var sqlStatement = connection.createStatement()) {
            sqlStatement.execute(statement);
        }
    }
}

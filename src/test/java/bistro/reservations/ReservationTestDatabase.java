package bistro.reservations;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
abstract class ReservationTestDatabase {
    static final String PATH = "/api/v1/reservations";
    static final String START = "2030-06-12T19:00:00-05:00";
    static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    RestTestClient http;

    @Autowired
    JdbcTemplate sql;

    @Autowired
    DataSource dataSource;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        String localUrl = System.getenv("BISTRO_TEST_DATABASE_URL");
        if (localUrl == null) {
            registry.add("spring.datasource.url", Container.POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", Container.POSTGRES::getUsername);
            registry.add("spring.datasource.password", Container.POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> localUrl);
            registry.add("spring.datasource.username", () -> System.getenv("BISTRO_TEST_DATABASE_USERNAME"));
            registry.add("spring.datasource.password", () -> System.getenv("BISTRO_TEST_DATABASE_PASSWORD"));
        }
    }

    private static class Container {
        static final PostgreSQLContainer POSTGRES = start();

        private static PostgreSQLContainer start() {
            PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
            postgres.start();
            return postgres;
        }
    }

    @BeforeEach
    void clearDatabase() {
        sql.execute("TRUNCATE reservation, dining_table");
    }

    void table(long id, int capacity) {
        sql.update("INSERT INTO dining_table (id, capacity) VALUES (?, ?)", id, capacity);
    }

    UUID seed(String status, Long tableId) {
        UUID code = UUID.randomUUID();
        sql.update("""
                INSERT INTO reservation
                    (reservation_code, customer_name, customer_email, reservation_time, party_size, status, assigned_table_id)
                VALUES (?, 'Ana Pérez', 'ana@example.org', ?, 4, ?, ?)
                """, code, OffsetDateTime.parse(START), status, tableId);
        return code;
    }

    List<StoredReservation> reservations() {
        return sql.query("""
                SELECT reservation_code, status, assigned_table_id, customer_name, customer_email, reservation_time, party_size
                FROM reservation ORDER BY reservation_code
                """, (row, number) -> new StoredReservation(
                row.getObject("reservation_code", UUID.class), row.getString("status"),
                row.getObject("assigned_table_id", Long.class), row.getString("customer_name"),
                row.getString("customer_email"), row.getObject("reservation_time", OffsetDateTime.class),
                row.getBigDecimal("party_size").toBigIntegerExact()));
    }

    record StoredReservation(UUID code, String status, Long tableId, String name, String email,
                             OffsetDateTime time, java.math.BigInteger size) {
    }

    static String request(String start, String size) {
        return """
                {"customerName":"Ana Pérez","customerEmail":"ana@example.org","reservationTime":"%s","partySize":%s}
                """.formatted(start, size);
    }

    RestTestClient.ResponseSpec post(String body) {
        return http.post().uri(PATH).contentType(APPLICATION_JSON).body(body).exchange();
    }

    Created created(String start, String size) {
        var response = post(request(start, size))
                .expectStatus().isCreated()
                .expectHeader().contentTypeCompatibleWith(APPLICATION_JSON)
                .expectBody().returnResult();
        JsonNode body = JSON.readTree(response.getResponseBodyContent());
        assertThat(body.size()).isEqualTo(2);
        UUID code = UUID.fromString(body.get("reservationCode").asString());
        assertThat(code.version()).isEqualTo(4);
        assertThat(response.getResponseHeaders().getLocation()).hasToString(PATH + "/" + code);
        return new Created(code, body.get("status").asString());
    }

    record Created(UUID code, String status) {
    }

    void problem(RestTestClient.ResponseSpec response, int status, String title, String detail) {
        var result = response.expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON)
                .expectBody().returnResult();
        JsonNode body = JSON.readTree(result.getResponseBodyContent());
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("title").asString()).isEqualTo(title);
        assertThat(body.get("detail").asString()).isEqualTo(detail);
    }
}

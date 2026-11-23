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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
abstract class ReservationTestDatabase {
    static final String PATH = "/api/v1/reservations";
    static final String START = "2030-06-12T19:00";
    static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    RestTestClient http;

    @Autowired
    JdbcTemplate sql;

    @Autowired
    DataSource dataSource;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ReservationTestDatabase::databaseUrl);
        registry.add("spring.datasource.username", ReservationTestDatabase::databaseUsername);
        registry.add("spring.datasource.password", ReservationTestDatabase::databasePassword);
    }

    static String databaseUrl() {
        String url = System.getenv("BISTRO_TEST_DATABASE_URL");
        return url == null ? Container.POSTGRES.getJdbcUrl() : url;
    }

    static String databaseUsername() {
        return System.getenv("BISTRO_TEST_DATABASE_URL") == null
                ? Container.POSTGRES.getUsername() : System.getenv("BISTRO_TEST_DATABASE_USERNAME");
    }

    static String databasePassword() {
        return System.getenv("BISTRO_TEST_DATABASE_URL") == null
                ? Container.POSTGRES.getPassword() : System.getenv("BISTRO_TEST_DATABASE_PASSWORD");
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
        sql.update("INSERT INTO dining_table (id, table_number, capacity) VALUES (?, ?, ?)",
                id, "M-" + id, capacity);
    }

    UUID seed(String status, Long tableId) {
        return seed(status, tableId, 4);
    }

    UUID seed(String status, Long tableId, int partySize) {
        UUID code = UUID.randomUUID();
        sql.update("""
                INSERT INTO reservation
                    (reservation_code, customer_name, customer_email, reservation_time, party_size, status, assigned_table_id)
                VALUES (?, 'Ana Pérez', 'ana@example.org', ?, ?, ?, ?)
                """, code, LocalDateTime.parse(START), partySize, status, tableId);
        return code;
    }

    List<StoredReservation> reservations() {
        return sql.query("""
                SELECT reservation_code, status, assigned_table_id, customer_name, customer_email, reservation_time, party_size
                FROM reservation ORDER BY reservation_code
                """, (row, number) -> new StoredReservation(
                row.getObject("reservation_code", UUID.class), row.getString("status"),
                row.getObject("assigned_table_id", Long.class), row.getString("customer_name"),
                row.getString("customer_email"), row.getObject("reservation_time", LocalDateTime.class),
                row.getInt("party_size")));
    }

    record StoredReservation(UUID code, String status, Long tableId, String name, String email,
                             LocalDateTime time, int size) {
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

    JsonNode problem(RestTestClient.ResponseSpec response, int status, String title, String detail) {
        var result = response.expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON)
                .expectBody().returnResult();
        JsonNode body = JSON.readTree(result.getResponseBodyContent());
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("title").asString()).isEqualTo(title);
        assertThat(body.get("detail").asString()).isEqualTo(detail);
        return body;
    }
}

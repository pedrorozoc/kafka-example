package bistro.reservations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

class CreateReservationHttpTest extends ReservationTestDatabase {
    private static final String INVALID_JSON =
            "Se requiere un cuerpo JSON válido con los tipos y formatos esperados.";

    @Test
    void resolvesAndStoresBothResultsWithDifferentCodes() {
        table(1, 4);
        Created confirmed = created(START, "4");
        Created rejected = created(START, "4");

        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(confirmed.code()).isNotEqualTo(rejected.code());
        assertThat(reservations()).containsExactlyInAnyOrder(
                stored(confirmed, 1L, "4"), stored(rejected, null, "4"));
    }

    @Test
    void rejectsWithEmptyCatalogAndDoesNotOccupyTablesForLaterRequests() {
        Created rejected = created(START, "4");
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(reservations()).containsExactly(stored(rejected, null, "4"));

        table(1, 4);
        assertThat(created(START, "4").status()).isEqualTo("CONFIRMED");
    }

    @Test
    void skipsOccupiedTableAndAssignsOneFreeTable() {
        table(1, 4);
        table(2, 4);
        seed("CONFIRMED", 1L);

        Created result = created(START, "4");
        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(reservations()).contains(stored(result, 2L, "4"));
    }

    @Test
    void rejectsValidGroupsWithoutOneSufficientlyLargeTable() {
        table(1, 4);
        table(2, 4);

        Created combined = created(START, "6");
        Created maximum = created(START, "12");
        assertThat(combined.status()).isEqualTo("REJECTED");
        assertThat(maximum.status()).isEqualTo("REJECTED");
        assertThat(reservations()).containsExactlyInAnyOrder(
                stored(combined, null, "6"), stored(maximum, null, "12"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "12", "12.0", "1.2e1", "0.0012e4", "1200e-2"})
    void confirmsValidBoundarySizesIncludingEquivalentJsonNumbers(String size) {
        table(1, 12);
        Created result = created(START, size);
        assertThat(result.status()).isEqualTo("CONFIRMED");
        String storedSize = size.equals("1") ? "1" : "12";
        assertThat(reservations()).containsExactly(stored(result, 1L, storedSize));
    }

    @Test
    void rejectsEquivalentOffsetsAcrossMidnight() {
        table(1, 4);
        Created first = created(START, "4");
        Created equivalent = created("2030-06-13T00:00:00Z", "4");

        assertThat(first.status()).isEqualTo("CONFIRMED");
        assertThat(equivalent.status()).isEqualTo("REJECTED");
        assertThat(reservations()).containsExactlyInAnyOrder(
                stored(first, 1L, "4"), stored(equivalent, null, "4"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2030-06-12T19:01:00-05:00", "2030-06-13T19:00:00-05:00"})
    void reusesTheSameTableAtADifferentInstant(String nextStart) {
        table(1, 4);
        assertThat(created(START, "4").status()).isEqualTo("CONFIRMED");
        assertThat(created(nextStart, "4").status()).isEqualTo("CONFIRMED");
        assertThat(reservations()).extracting(StoredReservation::tableId).containsExactly(1L, 1L);
    }

    @Test
    void preservesMicrosecondsAndAcceptsExplicitOffsetsAcrossTheFullRange() {
        table(1, 4);
        assertThat(created("2030-06-12t19:00:00.0000011234-05:00", "4").status()).isEqualTo("CONFIRMED");
        assertThat(created("2030-06-13T23:59:00.000001+23:59", "4").status()).isEqualTo("CONFIRMED");
        assertThat(created("2030-06-13T23:59:00.000001123400+23:59", "4").status()).isEqualTo("REJECTED");
        assertThat(reservations()).allSatisfy(row ->
                assertThat(row.time()).isEqualTo(OffsetDateTime.parse("2030-06-13T00:00:00.000001Z")));
    }

    @Test
    void distinguishesSubmicrosecondInstantsAndRejectsEquivalentRemainders() {
        table(1, 4);
        Created first = created("2030-06-12T19:00:00.0000009Z", "4");
        Created distinct = created("2030-06-12T19:00:00.0000001Z", "4");
        Created equivalent = created("2030-06-12T19:00:00.0000009000Z", "4");

        assertThat(first.status()).isEqualTo("CONFIRMED");
        assertThat(distinct.status()).isEqualTo("CONFIRMED");
        assertThat(equivalent.status()).isEqualTo("REJECTED");
        assertThat(reservations()).filteredOn(row -> row.tableId() != null)
                .extracting(StoredReservation::code).containsExactlyInAnyOrder(first.code(), distinct.code());
        assertThat(sql.query("SELECT reservation_code, reservation_time_remainder FROM reservation",
                (row, number) -> Map.entry(row.getObject("reservation_code", UUID.class), row.getString(2))))
                .containsExactlyInAnyOrder(Map.entry(first.code(), "9"), Map.entry(distinct.code(), "1"),
                        Map.entry(equivalent.code(), "9"));
    }

    @ParameterizedTest(name = "[{index}]")
    @MethodSource("partySizesAboveMaximum")
    void rejectsPartySizesAboveMaximumEvenWithAnAvailableTable(String size) {
        table(1, 4);
        seed("CONFIRMED", 1L);
        table(2, 13);
        var before = reservations();

        var response = post(request(START, size)).expectStatus().isBadRequest()
                .expectHeader().doesNotExist("Location");
        var body = problem(response, 400, "Solicitud inválida",
                "La cantidad de comensales no puede superar las 12 personas.");
        assertThat(body.has("reservationCode")).isFalse();
        assertThat(reservations()).isEqualTo(before);
    }

    static Stream<String> partySizesAboveMaximum() {
        return Stream.of("13", "13.0", "2147483648", "1" + "0".repeat(1100),
                "1e131072", "1.31e2147483648", "1" + "0".repeat(131_072));
    }

    @Test
    void reusesTheSameTableAfterMidnight() {
        table(1, 4);
        assertThat(created("2030-06-12T23:30:00-05:00", "4").status()).isEqualTo("CONFIRMED");
        assertThat(created("2030-06-13T00:30:00-05:00", "4").status()).isEqualTo("CONFIRMED");
        assertThat(reservations()).extracting(StoredReservation::tableId).containsExactly(1L, 1L);
    }

    @ParameterizedTest
    @MethodSource("missingOrNullFields")
    void rejectsMissingAndNullFields(InvalidRequest invalid) {
        assertInvalid(invalid.body(), invalid.detail());
    }

    static Stream<InvalidRequest> missingOrNullFields() {
        return Stream.of(
                new InvalidRequest("customerName", "El nombre del cliente es obligatorio."),
                new InvalidRequest("customerEmail", "El correo electrónico es obligatorio."),
                new InvalidRequest("reservationTime", "La fecha y hora de la reserva son obligatorias."),
                new InvalidRequest("partySize", "La cantidad de comensales es obligatoria.")
        ).flatMap(field -> {
            var missing = (ObjectNode) JSON.readTree(request(START, "4"));
            var explicitNull = missing.deepCopy();
            missing.remove(field.body());
            explicitNull.putNull(field.body());
            return Stream.of(new InvalidRequest(missing.toString(), field.detail()),
                    new InvalidRequest(explicitNull.toString(), field.detail()));
        });
    }

    @ParameterizedTest
    @MethodSource("invalidValues")
    void rejectsInvalidValuesAndTypes(InvalidRequest invalid) {
        assertInvalid(invalid.body(), invalid.detail());
    }

    static Stream<InvalidRequest> invalidValues() {
        String valid = request(START, "4");
        return Stream.of(
                new InvalidRequest(valid.replace("Ana Pérez", "   "), "El nombre del cliente es obligatorio."),
                new InvalidRequest(valid.replace("ana@example.org", "correo"), "El correo electrónico debe tener un formato válido."),
                new InvalidRequest(request("ayer", "4"), INVALID_JSON),
                new InvalidRequest(request("2030-06-12T19:00:00", "4"), INVALID_JSON),
                new InvalidRequest(request("2030-06-12T19:00Z", "4"), INVALID_JSON),
                new InvalidRequest(request("2030-02-30T19:00:00Z", "4"), INVALID_JSON),
                new InvalidRequest(request(START, "2.5"), INVALID_JSON),
                new InvalidRequest(request(START, "13.0000000000001"), INVALID_JSON),
                new InvalidRequest(request(START, "1e-2147483649"), INVALID_JSON),
                new InvalidRequest(request(START, "\"4\""), INVALID_JSON),
                new InvalidRequest(request(START, "true"), INVALID_JSON),
                new InvalidRequest(request(START, "[]"), INVALID_JSON),
                new InvalidRequest(valid.replace("\"Ana Pérez\"", "42"), INVALID_JSON),
                new InvalidRequest(valid.replace("\"ana@example.org\"", "{}"), INVALID_JSON),
                new InvalidRequest(valid.replace("\"" + START + "\"", "123"), INVALID_JSON),
                new InvalidRequest("{", INVALID_JSON),
                new InvalidRequest(valid + " {}", INVALID_JSON));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void rejectsNonpositivePartySize(String size) {
        assertInvalid(request(START, size), "La cantidad de comensales debe ser un entero positivo.");
    }

    @Test
    void rejectsAbsentBody() {
        problem(http.post().uri(PATH).contentType(APPLICATION_JSON).exchange(),
                400, "Solicitud inválida", INVALID_JSON);
        assertThat(reservations()).isEmpty();
    }

    private void assertInvalid(String body, String detail) {
        table(1, 4);
        seed("CONFIRMED", 1L);
        var before = reservations();
        problem(post(body), 400, "Solicitud inválida", detail);
        assertThat(reservations()).isEqualTo(before);
    }

    private StoredReservation stored(Created created, Long tableId, String size) {
        return new StoredReservation(created.code(), created.status(), tableId, "Ana Pérez", "ana@example.org",
                OffsetDateTime.parse("2030-06-13T00:00:00Z"), new BigInteger(size));
    }

    record InvalidRequest(String body, String detail) {
    }
}

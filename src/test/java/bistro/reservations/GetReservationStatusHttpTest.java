package bistro.reservations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

class GetReservationStatusHttpTest extends ReservationTestDatabase {
    @Test
    void readsStoredFinalDecisionsWithoutReevaluatingAvailability() {
        table(1, 4);
        UUID confirmed = seed("CONFIRMED", 1L);
        UUID rejected = seed("REJECTED", null);
        var before = reservations();
        assertStatus(confirmed, "CONFIRMED");
        assertStatus(rejected, "REJECTED");

        table(2, 4);
        for (int attempt = 0; attempt < 2; attempt++) {
            assertStatus(confirmed, "CONFIRMED");
            assertStatus(rejected, "REJECTED");
        }
        assertThat(reservations()).isEqualTo(before);
    }

    @Test
    void readsHistoricalReservationAboveCreationLimitWithoutChangingItsAssignment() {
        table(1, 13);
        UUID historical = seed("CONFIRMED", 1L, 13);
        var before = reservations();

        assertStatus(historical, "CONFIRMED");
        assertThat(reservations()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000-0000-0000-0000-000000000000", "codigo-desconocido", "1-1-1-1-1"})
    void returnsSpanishNotFoundForMissingAndMalformedCodes(String code) {
        var existing = seed("REJECTED", null);
        problem(get(code), 404, "Reserva no encontrada", "No existe una reserva con ese código.");
        assertThat(reservations()).hasSize(1).allSatisfy(row -> assertThat(row.code()).isEqualTo(existing));
    }

    @Test
    void returnsSpanishSystemErrorWhenLookupFails() {
        UUID existing = seed("REJECTED", null);
        var before = reservations();
        sql.execute("ALTER TABLE reservation RENAME TO reservation_unavailable");
        try {
            problem(get(existing.toString()), 500, "Error del sistema", "No se pudo completar la operación.");
        } finally {
            sql.execute("ALTER TABLE reservation_unavailable RENAME TO reservation");
        }
        assertThat(reservations()).isEqualTo(before);
    }

    private RestTestClient.ResponseSpec get(String code) {
        return http.get().uri(PATH + "/{code}", code).exchange();
    }

    private void assertStatus(UUID code, String status) {
        var result = get(code.toString()).expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(APPLICATION_JSON)
                .expectBody().returnResult();
        assertThat(JSON.readTree(result.getResponseBodyContent()))
                .isEqualTo(JSON.readTree("{\"status\":\"" + status + "\"}"));
    }
}

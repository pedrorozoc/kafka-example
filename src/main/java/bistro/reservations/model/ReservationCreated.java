// Record REservationCreated

package bistro.reservations.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationCreated(
        UUID reservationId,
        Integer partySize,
        LocalDateTime occurredAt
) {
}
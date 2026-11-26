// Record REservationCreated

package bistro.reservations.events;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationCreated(
        UUID reservationId,
        Integer partySize,
        LocalDateTime occurredAt
) {
}
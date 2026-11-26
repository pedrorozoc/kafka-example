package bistro.reservations.events;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationRejected(
        UUID reservationId,
        String customerEmail,
        String reason,
        LocalDateTime occuredAt
) {
}

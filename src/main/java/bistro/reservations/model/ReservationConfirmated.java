package bistro.reservations.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationConfirmated(
        UUID reservationId,
        String customerEmail,
        String tableNumber,
        LocalDateTime reservationTime,
        LocalDateTime occurredAt
) {
}

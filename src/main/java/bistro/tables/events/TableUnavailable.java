package bistro.tables.events;

import java.time.LocalDateTime;
import java.util.UUID;

public record TableUnavailable(
        UUID reservationId,
        String reason,
        LocalDateTime occuredAt
) {
}

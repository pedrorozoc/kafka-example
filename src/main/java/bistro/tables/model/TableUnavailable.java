package bistro.tables.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TableUnavailable(
        UUID reservationId,
        String reason,
        LocalDateTime occuredAt
) {
}

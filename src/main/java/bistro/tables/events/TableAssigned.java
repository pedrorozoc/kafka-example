package bistro.tables.events;

import java.time.LocalDateTime;
import java.util.UUID;

public record TableAssigned(
    UUID reservationId,
    Long tableId,
    String tableNumber,
    LocalDateTime occuredAt
) {
}

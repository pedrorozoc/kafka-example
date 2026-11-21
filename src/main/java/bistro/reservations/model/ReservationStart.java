package bistro.reservations.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Instant;

@Embeddable
public record ReservationStart(
        @Column(name = "reservation_time", nullable = false) Instant microsecond,
        @Column(name = "reservation_time_remainder", nullable = false, columnDefinition = "text") String remainder
) {
    public ReservationStart {
        // La forma canónica permite comparar las fracciones como texto ordenado por dígitos.
        int end = remainder.length();
        while (end > 0 && remainder.charAt(end - 1) == '0') {
            end--;
        }
        remainder = remainder.substring(0, end);
    }
}

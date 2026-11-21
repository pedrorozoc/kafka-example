package bistro.reservations.service;

import bistro.reservations.model.Reservation;
import bistro.reservations.model.ReservationStart;
import bistro.reservations.repository.ReservationRepository;
import bistro.tables.service.TableCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;

@Service
public class CreateReservationService {
    private static final Duration SLOT_DURATION = Duration.ofMinutes(90);

    private final ReservationRepository reservations;
    private final TableCatalog tables;

    public CreateReservationService(ReservationRepository reservations, TableCatalog tables) {
        this.reservations = reservations;
        this.tables = tables;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation create(String name, String email, ReservationStart start, BigInteger partySize) {
        Reservation reservation = new Reservation(name, email, start, partySize);
        List<Long> candidates = tables.lockCandidates(partySize);
        if (candidates.isEmpty()) {
            reservation.reject();
        } else {
            resolve(reservation, candidates);
        }
        return reservations.save(reservation);
    }

    private void resolve(Reservation reservation, List<Long> candidates) {
        ReservationStart start = reservation.getReservationTime();
        // La segunda sentencia ve las confirmaciones realizadas durante la espera por los bloqueos.
        var occupied = reservations.findOccupiedTableIds(candidates,
                start.microsecond().minus(SLOT_DURATION), start.microsecond().plus(SLOT_DURATION), start.remainder());
        candidates.stream()
                .filter(id -> !occupied.contains(id))
                .findFirst()
                .ifPresentOrElse(reservation::confirm, reservation::reject);
    }
}

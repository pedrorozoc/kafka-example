package bistro.reservations.service;

import bistro.reservations.model.Reservation;
import bistro.reservations.model.ReservationStatus;
import bistro.reservations.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class GetReservationStatusService {
    private static final Pattern UUID_FORMAT = Pattern.compile(
            "[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

    private final ReservationRepository reservations;

    public GetReservationStatusService(ReservationRepository reservations) {
        this.reservations = reservations;
    }

    @Transactional(readOnly = true)
    public Reservation findByCode(String code) {
        if (!UUID_FORMAT.matcher(code).matches()) {
            throw new ReservationNotFoundException();
        }
        Reservation reservation = reservations.findById(UUID.fromString(code))
                .orElseThrow(ReservationNotFoundException::new);
        if (reservation.getStatus() == ReservationStatus.PENDING) {
            throw new IllegalStateException("La reserva guardada no tiene una decisión definitiva.");
        }
        return reservation;
    }
}

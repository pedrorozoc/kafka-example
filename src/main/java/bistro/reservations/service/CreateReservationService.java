package bistro.reservations.service;

import bistro.reservations.model.Reservation;
import bistro.reservations.model.ReservationCreated;
import bistro.reservations.repository.ReservationRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CreateReservationService {
    private final ReservationRepository reservations;
    private KafkaTemplate<String, ReservationCreated>  kafkaTemplate;

    public CreateReservationService(ReservationRepository reservations) {
        this.reservations = reservations;

    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation create(String name, String email, LocalDateTime reservationTime, Integer partySize) {
        Reservation reservation = new Reservation(name, email, reservationTime, partySize);

        var saved=reservations.save(reservation);

        //construimos el evento, pues la reserva ya se guardó
        //ya pasó el evento. Ahora lo vamos a enviar
        ReservationCreated event = new ReservationCreated(
                saved.getId(),
                saved.getPartySize(),
                LocalDateTime.now()
        );
        //Enviar el evento al tópico y con el key que corresponde
        kafkaTemplate.send("reservation-created",String.valueOf(saved.getId()),event);
        return saved;
    }

    private void resolve(Reservation reservation, List<Long> candidates) {
        // La segunda sentencia ve las confirmaciones realizadas durante la espera por los bloqueos.
        var occupied = reservations.findOccupiedTableIds(candidates, reservation.getReservationTime());
        candidates.stream()
                .filter(id -> !occupied.contains(id))
                .findFirst()
                .ifPresentOrElse(reservation::confirm, reservation::reject);
    }
}

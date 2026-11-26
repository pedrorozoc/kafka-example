package bistro.reservations.service;

import bistro.reservations.model.*;
import bistro.reservations.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
@Slf4j
public class CreateReservationService {
    private final ReservationRepository reservations;
    //Herramienta de spring para enviar a kafka
    private final KafkaTemplate<String, Object>  kafkaTemplate;

    @Transactional
    public void confirm(UUID reservationId, Long tableId, String tableNumber) {
        Reservation reservation= reservations.findById(reservationId)
                .orElseThrow(()-> new IllegalArgumentException(
                        "Reserva no encontrada: "+ reservationId
                ));
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setAssignedTableId(tableId);
        reservations.save(reservation);

        log.info("Reserva {} confirmada con mesa {}", reservation.getId(),tableNumber);

        ReservationConfirmated confirmated=new ReservationConfirmated(
                reservationId,reservation.getCustomerEmail()
                ,tableNumber,reservation.getReservationTime()
                ,LocalDateTime.now()
        );

        kafkaTemplate.send("reservation-confirmed", String.valueOf(reservation.getId()), confirmated);


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

    @Transactional
    public void reject(UUID reservationId, String reason) {
        Reservation reservation=reservations.findById(reservationId)
                .orElseThrow(()-> new IllegalArgumentException("Reserva no encontrada: " + reservationId));
        reservation.setStatus(ReservationStatus.REJECTED);
        reservations.save(reservation);
        log.info("Reserva {} rejecida con mesa {}", reservation.getId(), reason);
        ReservationRejected event = new ReservationRejected(
                reservationId,reservation.getCustomerEmail(),
                reason,
                LocalDateTime.now()
        );
        kafkaTemplate.send("reservation-rejected", String.valueOf(reservation.getId()), event);
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

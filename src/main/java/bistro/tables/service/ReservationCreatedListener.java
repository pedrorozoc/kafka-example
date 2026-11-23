package bistro.tables.service;

import bistro.reservations.model.ReservationCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor

public class ReservationCreatedListener {
    private final TableCatalog tableCatalog;

    @KafkaListener(topics = "reservation-created" , groupId = "tables")
    public void onReservationCreated(ReservationCreated event) {
        tableCatalog.assignTableFor(event.partySize()).ifPresentOrElse(
                tableCatalog-> log.info("Reserva {} -> mesa {} ({} lugares) asignada",
                        event.reservationId(), tableCatalog.getId(), tableCatalog.getCapacity()),

                ()-> log.info("Reserva {} -> sin mesa para {} personas",
                        event.reservationId(), event.partySize())
        );
    }
}

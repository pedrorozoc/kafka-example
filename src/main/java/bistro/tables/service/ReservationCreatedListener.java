package bistro.tables.service;

import bistro.reservations.model.ReservationCreated;
import bistro.tables.model.TableAssigned;
import bistro.tables.model.TableUnavailable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor

public class ReservationCreatedListener {
    private final TableCatalog tableCatalog;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "reservation-created" , groupId = "tables")
    public void onReservationCreated(ReservationCreated event) {
        tableCatalog.assignTableFor(event.partySize()).ifPresentOrElse(
                table -> {
                    log.info("Reserva {} -> mesa {} (ID {}, {} lugares) asignada",
                            event.reservationId(), table.getTableNumber(), table.getId(), table.getCapacity());
                    TableAssigned assigned=new TableAssigned(
                            event.reservationId(),
                            table.getId(),
                            table.getTableNumber(),
                            LocalDateTime.now()
                    );
                    kafkaTemplate.send("table-assigned", String.valueOf(event.reservationId()),assigned);
                },
                ()-> {
                    log.info("Reserva {} -> sin mesa para {} personas",
                            event.reservationId(), event.partySize());
                    TableUnavailable unavailable=new TableUnavailable(
                            event.reservationId(),
                            "Sin mesa disponible para "+ event.partySize()+" personas",
                            LocalDateTime.now()
                    );
                    kafkaTemplate.send("table-unavailable", String.valueOf(event.reservationId()),unavailable);
                }
        );
    }
}

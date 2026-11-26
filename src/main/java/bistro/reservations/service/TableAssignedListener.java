package bistro.reservations.service;


import bistro.tables.events.TableAssigned;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TableAssignedListener {
    private final CreateReservationService createReservationService;

    @KafkaListener(topics = "table-assigned" , groupId = "reservations")
    public void onTableAssigned(TableAssigned event) {
        createReservationService.confirm(event.reservationId(),event.tableId(),event.tableNumber());
    }
}

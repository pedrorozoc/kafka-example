package bistro.reservations.service;

import bistro.tables.model.TableUnavailable;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TableUnbavailableListener {
    private final CreateReservationService createReservationService;


    @KafkaListener( topics = "table-unavailable" , groupId = "reservations")
    public void onTableUnavailable(TableUnavailable tableUnavailable) {
        createReservationService.reject(tableUnavailable.reservationId(),tableUnavailable.reason());


    }
}

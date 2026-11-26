package bistro.notifications.service;

import bistro.reservations.events.ReservationConfirmated;
import bistro.reservations.events.ReservationRejected;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@KafkaListener (topics = {"reservation-confirmed","reservation-rejected"}, groupId = "notifications")
public class NotificationListener {

    private final NotificationService notificationService;

    @KafkaHandler
    public void onConfirmed(ReservationConfirmated event){
        notificationService.notifyConfirmed(event.customerEmail(),event.reservationId(),event.tableNumber());
    }

    @KafkaHandler
    public void onRejected(ReservationRejected event){
        notificationService.notifyRejected(event.customerEmail(),event.reservationId(),event.reason());
    }
}

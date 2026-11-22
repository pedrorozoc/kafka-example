package bistro.reservations.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservation")
@Data
public class Reservation implements Persistable<UUID> {
    @Id
    @Column(updatable = false)
    private UUID reservationCode;

    @Column(nullable = false, columnDefinition = "text")
    private String customerName;

    @Column(nullable = false, columnDefinition = "text")
    private String customerEmail;

    @Column(nullable = false)
    private LocalDateTime reservationTime;

    @Column(nullable = false)
    private Integer partySize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private ReservationStatus status;

    private Long assignedTableId;

    @Transient
    private boolean newReservation = true;

    protected Reservation() {
    }

    public Reservation(String customerName, String customerEmail, LocalDateTime reservationTime, Integer partySize) {
        this.reservationCode = UUID.randomUUID();
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.reservationTime = reservationTime;
        this.partySize = partySize;
        this.status = ReservationStatus.PENDING;
    }

    public void confirm(long tableId) {
        requirePending();
        assignedTableId = tableId;
        status = ReservationStatus.CONFIRMED;
    }

    public void reject() {
        requirePending();
        status = ReservationStatus.REJECTED;
    }

    private void requirePending() {
        if (status != ReservationStatus.PENDING) {
            throw new IllegalStateException("La reserva ya tiene una decisión definitiva.");
        }
    }

    public LocalDateTime getReservationTime() {
        return reservationTime;
    }

    public UUID getReservationCode() {
        return reservationCode;
    }

    @Override
    public UUID getId() {
        return reservationCode;
    }

    @Override
    public boolean isNew() {
        return newReservation;
    }

    @PostLoad
    @PostPersist
    private void markPersisted() {
        newReservation = false;
    }

    public ReservationStatus getStatus() {
        return status;
    }
}

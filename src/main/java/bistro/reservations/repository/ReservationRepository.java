package bistro.reservations.repository;

import bistro.reservations.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    @Query("""
            SELECT DISTINCT reservation.assignedTableId
            FROM Reservation reservation
            WHERE reservation.status = bistro.reservations.model.ReservationStatus.CONFIRMED
              AND reservation.assignedTableId IN (:tableIds)
              AND reservation.reservationTime = :reservationTime
            """)
    Set<Long> findOccupiedTableIds(@Param("tableIds") List<Long> tableIds,
                                   @Param("reservationTime") LocalDateTime reservationTime);
}

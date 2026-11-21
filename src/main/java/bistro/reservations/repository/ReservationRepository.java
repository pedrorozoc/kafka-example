package bistro.reservations.repository;

import bistro.reservations.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    @Query(value = """
            SELECT DISTINCT assigned_table_id
            FROM reservation
            WHERE status = 'CONFIRMED'
              AND assigned_table_id IN (:tableIds)
              AND reservation_time BETWEEN :after AND :before
              AND (reservation_time, reservation_time_remainder) > (:after, :remainder)
              AND (reservation_time, reservation_time_remainder) < (:before, :remainder)
            """, nativeQuery = true)
    Set<Long> findOccupiedTableIds(@Param("tableIds") List<Long> tableIds,
                                   @Param("after") Instant after, @Param("before") Instant before,
                                   @Param("remainder") String remainder);
}

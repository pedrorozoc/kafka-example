package bistro.tables.repository;

import bistro.tables.model.DiningTable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {

    List<DiningTable> findByCapacityGreaterThanEqualOrderByCapacityAsc(int partySize);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM DiningTable t where t.id= :id")
    Optional<DiningTable> findByIdForUpdate(@Param("id") long id);
}

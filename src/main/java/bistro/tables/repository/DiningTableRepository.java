package bistro.tables.repository;

import bistro.tables.model.DiningTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {
    @Query(value = """
            SELECT id
            FROM dining_table
            WHERE capacity >= :partySize
            ORDER BY id
            FOR UPDATE
            """, nativeQuery = true)
    List<Long> lockCandidates(@Param("partySize") int partySize);
}

package bistro.tables.service;

import bistro.tables.model.DiningTable;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

public interface TableCatalog {
    List<DiningTable> findCandidates(int partySize);
    @Transactional
    Optional<DiningTable> lockTable(Long tableId);

    @Transactional
    Optional<DiningTable> assignTableFor(int partySize);
}

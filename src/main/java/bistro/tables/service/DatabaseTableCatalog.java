package bistro.tables.service;

import bistro.tables.model.DiningTable;
import bistro.tables.repository.DiningTableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
class DatabaseTableCatalog implements TableCatalog {
    private final DiningTableRepository tables;

    DatabaseTableCatalog(DiningTableRepository tables) {
        this.tables = tables;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<DiningTable> findCandidates(int partySize) {
        return tables.findByCapacityGreaterThanEqualOrderByCapacityAsc(partySize);
    }

    @Transactional
    @Override
    public Optional<DiningTable> lockTable(Long tableId) {
        return tables.findByIdForUpdate(tableId);
    }

    @Transactional
    @Override
    public Optional<DiningTable> assignTableFor(int partySize) {
        for(DiningTable candidate:findCandidates(partySize)){
            Optional<DiningTable> locked=lockTable(candidate.getId());
            if(locked.isPresent()) {
                return locked;
            }
        }

        return Optional.empty();
    }
}

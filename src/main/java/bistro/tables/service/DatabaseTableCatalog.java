package bistro.tables.service;

import bistro.tables.repository.DiningTableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class DatabaseTableCatalog implements TableCatalog {
    private final DiningTableRepository tables;

    DatabaseTableCatalog(DiningTableRepository tables) {
        this.tables = tables;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> lockCandidates(int partySize) {
        return tables.lockCandidates(partySize);
    }
}

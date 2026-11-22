package bistro.tables.service;

import java.util.List;

public interface TableCatalog {
    List<Long> lockCandidates(int partySize);
}

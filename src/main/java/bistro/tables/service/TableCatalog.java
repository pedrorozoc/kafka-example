package bistro.tables.service;

import java.math.BigInteger;
import java.util.List;

public interface TableCatalog {
    List<Long> lockCandidates(BigInteger partySize);
}

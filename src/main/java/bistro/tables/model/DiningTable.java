package bistro.tables.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dining_table")
public class DiningTable {
    @Id
    private Long id;

    private int capacity;

    protected DiningTable() {
    }
}

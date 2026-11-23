package bistro.tables.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "dining_table")
@Data
public class DiningTable {
    @Id
    private Long id;

    private int capacity;

    protected DiningTable() {
    }
}

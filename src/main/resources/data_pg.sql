INSERT INTO dining_table (id, table_number, capacity)
VALUES
    (1, 'M01', 2),
    (2, 'M02', 4),
    (3, 'M03', 6),
    (4, 'M04', 8)
    ON CONFLICT (id) DO UPDATE
                            SET
                                table_number = EXCLUDED.table_number,
                            capacity = EXCLUDED.capacity;
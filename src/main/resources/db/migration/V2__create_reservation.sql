CREATE TABLE reservation (
    reservation_code uuid PRIMARY KEY,
    customer_name text NOT NULL CHECK (customer_name ~ '[^[:space:]]'),
    customer_email text NOT NULL CHECK (customer_email ~ '[^[:space:]]'),
    reservation_time timestamptz NOT NULL,
    party_size numeric NOT NULL CHECK (
        party_size > 0 AND party_size < 'Infinity'::numeric AND party_size = trunc(party_size)
    ),
    status text NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    assigned_table_id bigint REFERENCES dining_table (id),
    CHECK ((status = 'CONFIRMED') = (assigned_table_id IS NOT NULL))
);

CREATE INDEX reservation_occupied_table_time_idx
    ON reservation (assigned_table_id, reservation_time)
    WHERE status = 'CONFIRMED';

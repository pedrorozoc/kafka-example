CREATE TABLE dining_table (
    id bigint PRIMARY KEY,
    capacity integer NOT NULL CHECK (capacity > 0)
);

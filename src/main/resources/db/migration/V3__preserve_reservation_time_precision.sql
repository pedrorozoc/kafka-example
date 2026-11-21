ALTER TABLE reservation ADD COLUMN reservation_time_remainder text COLLATE "C" NOT NULL DEFAULT ''
    CHECK (reservation_time_remainder ~ '^([0-9]*[1-9])?$');

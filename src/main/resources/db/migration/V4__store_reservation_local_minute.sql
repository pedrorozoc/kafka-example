LOCK TABLE reservation IN ACCESS EXCLUSIVE MODE;

DO $migration$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_timezone_names WHERE name = '${restaurant_zone}') THEN
        RAISE EXCEPTION 'La zona histórica del restaurante no es válida. Revisa BISTRO_LEGACY_RESERVATION_ZONE.';
    END IF;
    IF EXISTS (SELECT 1 FROM reservation WHERE NOT isfinite(reservation_time)) THEN
        RAISE EXCEPTION 'Existe una reserva histórica con una fecha no finita. Revisa reservation_time antes de migrar.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM reservation
        WHERE party_size NOT BETWEEN 1 AND 2147483647 OR party_size <> trunc(party_size)
    ) THEN
        RAISE EXCEPTION 'Existe una cantidad histórica de comensales fuera del rango de integer. Revisa party_size antes de migrar.';
    END IF;
END
$migration$;

ALTER TABLE reservation
    DROP CONSTRAINT reservation_party_size_check,
    DROP COLUMN reservation_time_remainder,
    ALTER COLUMN reservation_time TYPE timestamp without time zone
        USING date_trunc('minute', reservation_time AT TIME ZONE '${restaurant_zone}'),
    ALTER COLUMN party_size TYPE integer USING party_size::integer,
    ADD CONSTRAINT reservation_time_local_minute_check
        CHECK (isfinite(reservation_time) AND reservation_time = date_trunc('minute', reservation_time)),
    ADD CONSTRAINT reservation_party_size_check CHECK (party_size > 0);

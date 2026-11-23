ALTER TABLE dining_table ADD COLUMN table_number varchar(255);

UPDATE dining_table SET table_number = CAST(id AS varchar(255));

ALTER TABLE dining_table ALTER COLUMN table_number SET NOT NULL;

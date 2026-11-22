# Validar el ciclo de reserva por HTTP

Ejecutar esta guía después de implementar el [plan](plan.md). Se requiere Java 25, Docker, Bash, curl y jq. Los comandos parten de la raíz del repositorio y usan una base desechable. Las pruebas automatizadas incluyen el arranque desde una base vacía y la actualización desde V3.

## Arrancar el servicio

Con el puerto local 55432 libre, arrancar PostgreSQL y el servicio. Flyway aplica las migraciones durante el arranque.

```bash
docker run --rm -d --name bistro-reservation-check -p 127.0.0.1:55432:5432 \
  -e POSTGRES_DB=bistro -e POSTGRES_USER=bistro -e POSTGRES_PASSWORD=local_bistro postgres:17
until docker exec bistro-reservation-check pg_isready -h localhost -U bistro -d bistro; do sleep 1; done
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/bistro
export SPRING_DATASOURCE_USERNAME=bistro SPRING_DATASOURCE_PASSWORD=local_bistro
export BISTRO_LEGACY_RESERVATION_ZONE=America/Bogota
./mvnw spring-boot:run
```

Desde otra terminal, cargar una mesa de 12 plazas y definir las funciones de prueba. Los encabezados de las solicitudes aparecen en stderr.

```bash
db() {
  docker exec -i bistro-reservation-check psql -U bistro -d bistro -v ON_ERROR_STOP=1 "$@"
}
db -c 'INSERT INTO dining_table (id, capacity) VALUES (1, 12)'
url=http://localhost:8080/api/v1/reservations
post() {
  local body
  body=$(jq -nc --arg at "$1" --argjson size "$2" \
    '{customerName:"Ana Pérez",customerEmail:"ana@example.org",reservationTime:$at,partySize:$size}')
  curl -sS -D /dev/stderr -H 'Content-Type: application/json' --data "$body" "$url"
}
```

## Crear, consultar y comprobar disponibilidad

Cada POST válido devuelve HTTP 201 y `Location`. La primera solicitud del minuto se confirma y la segunda queda rechazada. Las solicitudes a minutos distintos se confirman con la misma mesa. Consultar ambos códigos devuelve la decisión original.

```bash
first=$(post '2030-06-12T19:00' 12)
jq -e '.status == "CONFIRMED"' <<< "$first"
second=$(post '2030-06-12T19:00' 4)
jq -e '.status == "REJECTED"' <<< "$second"
post '2030-06-12T19:01' 1 | jq -e '.status == "CONFIRMED"'
post '2030-06-13T19:00' 12 | jq -e '.status == "CONFIRMED"'
for result in "$first" "$second"; do
  code=$(jq -er .reservationCode <<< "$result")
  expected=$(jq -er .status <<< "$result")
  curl -fsS "$url/$code" | jq -e --arg expected "$expected" '. == {status:$expected}'
done
curl -si "$url/00000000-0000-0000-0000-000000000000"
db -c 'SELECT reservation_time, party_size, status, assigned_table_id FROM reservation ORDER BY reservation_time, status'
```

El último GET devuelve 404 y `application/problem+json`. PostgreSQL muestra cuatro reservas con segundos en cero. La fila rechazada tiene mesa nula.

## Probar los errores de entrada

Los ejemplos devuelven HTTP 400 en formato `application/problem+json`, con título y detalle en español y sin encabezado `Location`. El número de reservas se mantiene. Las pruebas MVC verifican también que el servicio de creación recibe cero llamadas.

```bash
before=$(db -At -c 'SELECT count(*) FROM reservation')
for at in 2030-02-30T19:00 2030-06-14T24:00 2030-06-14T19:00:00 \
  2030-06-14T19:00:00.1 2030-06-14T19:00Z 2030-06-14T19:00-05:00; do
  post "$at" 4 | jq -e '.status == 400 and (.title | length > 0) and (.detail | length > 0)'
done
for size in 0 13 1.5 '"4"' 2147483648; do
  post '2030-06-14T19:00' "$size" | jq -e '.status == 400 and (has("reservationCode") | not)'
done
test "$before" = "$(db -At -c 'SELECT count(*) FROM reservation')"
```

## Probar solicitudes simultáneas

Con una sola mesa, dos solicitudes simultáneas para el mismo minuto producen una confirmación y un rechazo. Ambas devuelven HTTP 201 con códigos distintos.

```bash
results=$(mktemp -d)
post '2030-06-15T19:00' 4 > "$results/a" &
first_pid=$!
post '2030-06-15T19:00' 4 > "$results/b" &
second_pid=$!
wait "$first_pid" && wait "$second_pid"
jq -se '([.[].status] | sort) == ["CONFIRMED","REJECTED"] and ([.[].reservationCode] | unique | length) == 2' "$results/a" "$results/b"
```

## Comprobar la migración y ejecutar la suite

```bash
./mvnw -Dtest=ReservationMigrationTest test
./mvnw verify
```

`ReservationMigrationTest` prepara una base con V1 a V3 y aplica V4 sobre esa misma base. Debe comprobar que dos confirmaciones históricas de la misma mesa, separadas por segundos, conservan sus códigos, estados, asignaciones y cantidades. Las dos ocupan el mismo minuto local y la mesa queda disponible para el minuto siguiente. La fixture incluye un cambio de fecha respecto de UTC y una reserva histórica de 13 personas. La prueba verifica que los datos y el esquema V3 se conservan si la migración falla ante una zona inválida, una fecha histórica no finita o una cantidad histórica que excede el rango de `integer`.

La suite prueba números JSON de valor entero como `12.0` y `1.2e1`. También cubre la espera y el rollback de una transacción y comprueba que el enlace de `LocalDateTime` conserva el minuto con una zona JVM distinta, incluso para un minuto que coincida con un salto de horario de verano en esa zona. Las pruebas de integración usan Testcontainers o las variables `BISTRO_TEST_DATABASE_URL`, `BISTRO_TEST_DATABASE_USERNAME` y `BISTRO_TEST_DATABASE_PASSWORD` con una base exclusiva de pruebas. La prueba de migración aplica V3 y V4 en un esquema temporal. El usuario de la base de pruebas debe poder crear esquemas.

Detener el servicio con Ctrl+C en la primera terminal y liberar la base desechable con `docker stop bistro-reservation-check`. El [contrato HTTP](contracts/openapi.yaml) y el [modelo de datos](data-model.md) detallan los resultados esperados.

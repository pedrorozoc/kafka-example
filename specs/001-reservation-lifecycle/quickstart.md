# Validar el ciclo de reserva por HTTP

Esta guía valida el ciclo de reserva con disponibilidad por instante una vez implementado el plan. Usar Java 25, el Maven Wrapper del proyecto, Docker, Bash, curl y jq. Los comandos parten de la raíz del repositorio. El ejemplo usa una base desechable exclusiva para la prueba. El catálogo comienza con una mesa de cuatro plazas.

El [contrato HTTP](contracts/openapi.yaml) define las respuestas y los códigos de error. El [modelo de datos](data-model.md) define qué asignaciones comprobar en PostgreSQL.

### Arrancar el servicio

Arrancar un contenedor nuevo con el puerto local 55432 libre. La aplicación aplicará las migraciones de Flyway al iniciar.

```bash
docker run --rm -d --name bistro-reservation-check -p 127.0.0.1:55432:5432 \
  -e POSTGRES_DB=bistro -e POSTGRES_USER=bistro -e POSTGRES_PASSWORD=local_bistro postgres:17
until docker exec bistro-reservation-check pg_isready -h localhost -U bistro -d bistro; do sleep 1; done
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/bistro
export SPRING_DATASOURCE_USERNAME=bistro SPRING_DATASOURCE_PASSWORD=local_bistro
./mvnw spring-boot:run
```

Con el servicio iniciado, abrir otra terminal y preparar la fixture del catálogo antes del primer POST.

```bash
docker exec bistro-reservation-check psql -U bistro -d bistro -v ON_ERROR_STOP=1 \
  -c 'INSERT INTO dining_table (id, capacity) VALUES (1, 4)'
url=http://localhost:8080/api/v1/reservations
post() {
  local body
  body=$(jq -nc --arg at "$1" --argjson size "$2" \
    '{customerName:"Ana Pérez",customerEmail:"ana@example.org",reservationTime:$at,partySize:$size}')
  curl -sS -D /dev/stderr -H 'Content-Type: application/json' --data "$body" "$url"
}
```

### Crear y consultar

Ejecutar los comandos en la misma terminal y en este orden. Los encabezados aparecen en stderr. Cada POST válido debe devolver 201 y un encabezado Location con la ruta de consulta de su código.

```bash
first=$(post '2030-06-12T19:00:00-05:00' 4)
jq -e '.status == "CONFIRMED"' <<< "$first"
second=$(post '2030-06-13T00:00:00Z' 4)
jq -e '.status == "REJECTED"' <<< "$second"
post '2030-06-12T19:01:00-05:00' 4 | jq -e '.status == "CONFIRMED"'
for result in "$first" "$second"; do
  code=$(jq -er .reservationCode <<< "$result")
  expected=$(jq -er .status <<< "$result")
  curl -fsS "$url/$code" | jq -e --arg expected "$expected" '. == {status:$expected}'
done
post '2030-06-14T19:00:00-05:00' 0
curl -si "$url/00000000-0000-0000-0000-000000000000"
```

Las últimas dos respuestas deben ser 400 y 404 con `application/problem+json` y título y detalle en español. Tras esos errores, la base debe contener las tres reservas válidas. Los dos registros confirmados tendrán mesa 1 y el rechazado tendrá mesa nula.

```bash
docker exec bistro-reservation-check psql -U bistro -d bistro \
  -c 'SELECT status, assigned_table_id FROM reservation ORDER BY reservation_time'
```

### Comprobar precisión y reservas existentes

Con una sola mesa, estos POST prueban dos instantes distintos dentro del mismo microsegundo y una representación equivalente del primero.

```bash
post '2030-06-18T19:00:00.0000009Z' 4 | jq -e '.status == "CONFIRMED"'
post '2030-06-18T19:00:00.0000001Z' 4 | jq -e '.status == "CONFIRMED"'
post '2030-06-18T19:00:00.0000009000Z' 4 | jq -e '.status == "REJECTED"'
```

En la base desechable, preparar una reserva histórica que usa el valor por defecto del resto decimal. Comparar la misma fila antes y después de las solicitudes.

```bash
(
  set -euo pipefail
  legacy_code=815c306e-0ff9-4f34-8050-ed972d1ffcf5
  db() {
    docker exec -i bistro-reservation-check psql -U bistro -d bistro -v ON_ERROR_STOP=1 "$@"
  }
  db -c "INSERT INTO reservation
    (reservation_code, customer_name, customer_email, reservation_time, party_size, status, assigned_table_id)
    VALUES ('$legacy_code', 'Ana Pérez', 'ana@example.org', '2030-06-19T19:00:00Z', 4, 'CONFIRMED', 1)"
  legacy_row="SELECT reservation_code, reservation_time, reservation_time_remainder, status, assigned_table_id
    FROM reservation WHERE reservation_code = '$legacy_code'"
  before=$(db -At -c "$legacy_row")
  curl -fsS "$url/$legacy_code" | jq -e '. == {status:"CONFIRMED"}'
  post '2030-06-19T19:00:00Z' 4 | jq -e '.status == "REJECTED"'
  post '2030-06-19T19:01:00Z' 4 | jq -e '.status == "CONFIRMED"'
  test "$before" = "$(db -At -c "$legacy_row")"
)
```

### Probar solicitudes simultáneas

En la misma terminal, solicitar la única mesa dos veces en una fecha libre. Cada petición devuelve 201. Debe haber una reserva confirmada y una rechazada, con códigos distintos.

```bash
results=$(mktemp -d)
post '2030-06-15T19:00:00Z' 4 > "$results/a" &
first_pid=$!
post '2030-06-15T19:00:00Z' 4 > "$results/b" &
second_pid=$!
wait "$first_pid" && wait "$second_pid"
jq -se '([.[].status] | sort) == ["CONFIRMED","REJECTED"] and ([.[].reservationCode] | unique | length) == 2' "$results/a" "$results/b"
```

### Consultar un resultado definitivo

Después de probar la concurrencia, añadir una mesa para que la solicitud previamente rechazada tenga ahora capacidad disponible. Consultar dos veces los dos códigos originales debe seguir devolviendo 200 y la decisión recibida al crearlos.

```bash
docker exec bistro-reservation-check psql -U bistro -d bistro -v ON_ERROR_STOP=1 \
  -c 'INSERT INTO dining_table (id, capacity) VALUES (2, 4)'
for result in "$first" "$second" "$first" "$second"; do
  code=$(jq -er .reservationCode <<< "$result")
  expected=$(jq -er .status <<< "$result")
  curl -fsS "$url/$code" | jq -e --arg expected "$expected" '. == {status:$expected}'
done
post '2030-06-16T19:00:00Z' 6 | jq -e '.status == "REJECTED"'
curl -si "$url/codigo-desconocido"
```

El POST devuelve 201. Las mesas de cuatro plazas siguen libres, incluso cuando la capacidad total alcanza para seis personas. El último GET devuelve 404 con el problema en español.

### Validar el máximo de 12 comensales

Con las mesas actuales, un grupo de 12 recibe `201 REJECTED`. Añade una mesa de 13 plazas y envía una solicitud de 13 para el mismo instante. Debe devolver 400 en formato `application/problem+json`, con título y detalle en español, sin `Location` ni fila nueva. Para ese mismo instante, un grupo de 12 debe recibir `201 CONFIRMED` con la nueva mesa.

```bash
(
  set -euo pipefail
  instant='2030-06-17T19:00:00Z'
  post "$instant" 12 | jq -e '.status == "REJECTED"'
  docker exec bistro-reservation-check psql -U bistro -d bistro -v ON_ERROR_STOP=1 \
    -c 'INSERT INTO dining_table (id, capacity) VALUES (3, 13)'
  count_reservations() {
    docker exec bistro-reservation-check psql -U bistro -d bistro -At \
      -c 'SELECT count(*) FROM reservation'
  }
  before=$(count_reservations)
  for size in 13 2147483648; do
    post "$instant" "$size" 2>"$results/invalid-size.headers" | jq -e '
      .status == 400 and .title == "Solicitud inválida"
      and .detail == "La cantidad de comensales no puede superar las 12 personas."
      and (has("reservationCode") | not)'
    grep -Eq '^HTTP/[^ ]+ 400' "$results/invalid-size.headers"
    grep -Eiq '^content-type: application/problem\+json' "$results/invalid-size.headers"
    if grep -Eiq '^location:' "$results/invalid-size.headers"; then
      printf '%s\n' 'La respuesta 400 no debe incluir Location.' >&2
      exit 1
    fi
    test "$before" = "$(count_reservations)"
  done
  post "$instant" 12 | jq -e '.status == "CONFIRMED"'
  docker exec bistro-reservation-check psql -U bistro -d bistro -At \
    -c "SELECT assigned_table_id FROM reservation WHERE reservation_time = '$instant' AND status = 'CONFIRMED'" \
    | grep -qx '3'
)
```

### Suite de aceptación y cierre

```bash
./mvnw verify
```

Las pruebas de integración usan PostgreSQL de Testcontainers y fixtures aisladas. También se pueden ejecutar con `BISTRO_TEST_DATABASE_URL`, `BISTRO_TEST_DATABASE_USERNAME` y `BISTRO_TEST_DATABASE_PASSWORD` apuntando a una base exclusiva de pruebas. La suite vacía las tablas de reservas y mesas al preparar cada caso.

- Para cada campo obligatorio, omitirlo y enviarlo como `null`. Probar también nombre en blanco, email inválido, hora ilegible o sin desplazamiento, cantidad negativa, fraccionaria o enviada como cadena. Para cada entrada inválida, verificar 400 en español y el mismo número de reservas y asignaciones antes y después.
- Probar los límites 1, 12 y 13. Conservar el tratamiento de números JSON de valor entero, incluido `12.0`, y comprobar 400 para `13.0` y los enteros grandes que antes se guardaban. Una prueba MVC debe verificar que el servicio de creación recibe cero llamadas al enviar 13. Una reserva histórica superior a 12, preparada en la base de pruebas, debe seguir devolviendo su estado al consultarla por código.
- Probar catálogo vacío y una mesa asignada con otra apta disponible. Una mesa con reserva a las 23:30 debe poder confirmarse a las 00:30 del día siguiente en el mismo desplazamiento. Verificar aparte que desplazamientos distintos que representan el mismo instante producen conflicto.
- Coordinar dos conexiones independientes en el punto de bloqueo, con una sola mesa apta. Comprobar que la segunda lee la decisión de la primera después del commit. Simular además un fallo de escritura y verificar que el rollback deja la mesa disponible.

Detener el servicio con Ctrl+C en la primera terminal y liberar la base desechable con `docker stop bistro-reservation-check`. Para repetir los ejemplos con las mismas fechas, comenzar con un contenedor nuevo.

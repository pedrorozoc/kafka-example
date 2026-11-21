# Validar el ciclo de reserva por HTTP

Esta guía describe la ejecución prevista una vez implementado el plan. Usar Java 21, el Maven Wrapper del proyecto, Docker, Bash, curl y jq. Los comandos parten de la raíz del repositorio. El ejemplo usa una base desechable exclusiva para la prueba y una sola mesa de cuatro plazas.

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
second=$(post '2030-06-12T20:00:00-05:00' 4)
jq -e '.status == "REJECTED"' <<< "$second"
post '2030-06-12T20:30:00-05:00' 4 | jq -e '.status == "CONFIRMED"'
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

### Suite de aceptación y cierre

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
post '2030-06-16T19:00:00Z' 2147483648 | jq -e '.status == "REJECTED"'
curl -si "$url/codigo-desconocido"
```

Los dos POST devuelven 201. Las mesas de cuatro plazas siguen libres, incluso cuando la capacidad total alcanza para seis personas. El último GET devuelve 404 con el problema en español.

```bash
./mvnw verify
```

Las pruebas de integración previstas usarán PostgreSQL de Testcontainers con fixtures aisladas y cubrirán ambas historias de la [especificación](spec.md).

- Para cada campo obligatorio, omitirlo y enviarlo como `null`. Probar también nombre en blanco, email inválido, hora ilegible o sin desplazamiento, cantidad negativa, fraccionaria o enviada como cadena. Para cada entrada inválida, verificar 400 en español y el mismo número de reservas y asignaciones antes y después.
- Probar catálogo vacío, una mesa ocupada y otra apta libre, offsets equivalentes y cruce de medianoche. Una reserva a las 23:30 debe impedir otra a las 00:30 y permitirla a la 01:00 del día siguiente con la misma mesa.
- Coordinar dos conexiones independientes en el punto de bloqueo, con una sola mesa apta. Comprobar que la segunda lee la decisión de la primera después del commit. Simular además un fallo de escritura y verificar que el rollback deja la mesa disponible.

Detener el servicio con Ctrl+C en la primera terminal y liberar la base desechable con `docker stop bistro-reservation-check`. Para repetir los ejemplos con las mismas fechas, comenzar con un contenedor nuevo.

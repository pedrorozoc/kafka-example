# Bistro

API HTTP para solicitar una reserva y consultar su estado.

## Arranque local

Desde una copia del repositorio, usa JDK 25 y una base PostgreSQL 17. El Maven
Wrapper descarga Maven 3.9.12 en la primera ejecución. Docker es necesario para
ejecutar las pruebas con Testcontainers.

Levanta PostgreSQL, pgAdmin y Kafka con `docker compose up -d`. Para levantar
solo la base y pgAdmin, usa `docker compose up -d pgadmin`.

Abre pgAdmin en <http://localhost:8082>, inicia sesión con `admin@bistro.dev`
y `local_pgadmin`, y selecciona **Servers → Bistro local**. PostgreSQL escucha
en `localhost:55432` y ambos servicios guardan sus datos en volúmenes de Docker.

Configura la conexión de la API con las credenciales locales de PostgreSQL.

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/bistro
export SPRING_DATASOURCE_USERNAME=bistro
export SPRING_DATASOURCE_PASSWORD=local_bistro
./mvnw spring-boot:run
```

Al arrancar, Flyway aplica las migraciones y JPA valida el esquema. La API
escucha en el puerto 8080. El catálogo se carga como dato preexistente.
Cada mesa tiene un `table_number` obligatorio de hasta 255 caracteres, como
`T-01`. La migración V5 inicializa el número de las mesas existentes con su
`id` en texto y conserva las asignaciones de las reservas.

El POST de reservas recibe la fecha y hora local a precisión de minuto, por
ejemplo `2030-06-12T19:00`, y una cantidad entera de 1 a 12 comensales.

Al actualizar una base existente desde V3, detén las instancias anteriores y
respalda la base antes de arrancar la nueva versión. Flyway convierte las fechas
históricas a la zona IANA indicada mediante `BISTRO_LEGACY_RESERVATION_ZONE`.
El valor predeterminado es `America/Bogota`. Configura la zona histórica real
del restaurante antes de aplicar V4. Actualiza los clientes del POST para que
envíen el nuevo formato de fecha y hora.

Exclusivamente en la base desechable de validación, ejecuta este SQL tras
arrancar para cargar la mesa de cuatro plazas del ejemplo.

```sql
INSERT INTO dining_table (id, table_number, capacity) VALUES (1, 'T-01', 4);
```

Ejecuta `./mvnw verify` para probar la API con PostgreSQL 17 y construir el JAR.
El artefacto ejecutable queda en `target/bistro-0.0.1-SNAPSHOT.jar`. Se puede
arrancar con:
```SPRING_PROFILES_ACTIVE=prod \
MAIL_HOST=localhost \
MAIL_PORT=1025 \
java -jar target/bistro-0.0.1-SNAPSHOT.jar
```
y las mismas variables.

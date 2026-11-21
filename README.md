# Bistro

API HTTP para solicitar una reserva y consultar su estado.

## Arranque local

Desde una copia del repositorio, usa JDK 25 y una base PostgreSQL 17. El Maven
Wrapper descarga Maven 3.9.12 en la primera ejecución. Docker es necesario para
ejecutar las pruebas con Testcontainers.

Configura la conexión a una base existente con permisos para aplicar las
migraciones. Este ejemplo usa las credenciales de la base desechable de la
[guía de validación](specs/001-reservation-lifecycle/quickstart.md).

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/bistro
export SPRING_DATASOURCE_USERNAME=bistro
export SPRING_DATASOURCE_PASSWORD=local_bistro
./mvnw spring-boot:run
```

Al arrancar, Flyway aplica las migraciones y JPA valida el esquema. La API
escucha en el puerto 8080. El catálogo se carga como dato preexistente.

Exclusivamente en la base desechable de validación, ejecuta este SQL tras
arrancar para cargar la mesa de cuatro plazas del ejemplo.

```sql
INSERT INTO dining_table (id, capacity) VALUES (1, 4);
```

Ejecuta `./mvnw verify` para probar la API con PostgreSQL 17 y construir el JAR.
El artefacto ejecutable queda en `target/bistro-0.0.1-SNAPSHOT.jar`. Se puede
arrancar con `java -jar target/bistro-0.0.1-SNAPSHOT.jar` y las mismas variables.

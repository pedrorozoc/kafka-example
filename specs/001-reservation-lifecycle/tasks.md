---
description: "Tareas de implementación para el ciclo de vida de una reserva"
---

# Tareas del ciclo de vida de una reserva

**Diseño** · [Plan](plan.md), [especificación](spec.md), [investigación](research.md), [modelo](data-model.md), [contrato HTTP](contracts/openapi.yaml) y [guía de validación](quickstart.md).

**Formato** · `[ID] [P?] [USn?] Descripción con ruta desde la raíz`. `[P]` identifica trabajo que puede coincidir con otra tarea indicada en la sección de dependencias, una vez resueltos los prerrequisitos de ambas. `[USn]` identifica la historia.

T001–T026 registran la entrega original. El incremento de FR-013 empieza en la fase 6. T027 reemplaza la expectativa histórica de T012 para cantidades superiores a 12. T029 amplía la validación de entrada definida en T016.

## Fase 1 — Preparación

- [X] T001 Configurar Java 25 y un único JAR ejecutable Spring Boot 4.2.0 en `pom.xml`, con Spring MVC, Data JPA, Bean Validation, PostgreSQL JDBC, Flyway y su módulo PostgreSQL, MapStruct 1.6.3 con procesador de la misma versión y componentes Spring, JUnit Jupiter, soporte de pruebas HTTP de Spring Boot y Testcontainers PostgreSQL.
- [X] T002 [P] Incorporar Maven Wrapper 3.9.x en `mvnw` y `.mvn/wrapper/maven-wrapper.properties`, con el script ejecutable desde la raíz del repositorio.
- [X] T003 [P] Crear el punto de entrada en `src/main/java/bistro/BistroApplication.java` y configurar el datasource por entorno, Flyway y la validación del esquema por JPA en `src/main/resources/application.yaml`.
- [X] T004 Documentar en `README.md` el arranque desde una copia nueva con Java 25, Maven Wrapper y PostgreSQL 17, las variables de conexión, la migración al arrancar y cómo cargar la mesa de cuatro plazas de `specs/001-reservation-lifecycle/quickstart.md` exclusivamente en una base de validación.

## Fase 2 — Fundamentos compartidos

- [X] T005 [P] Crear `dining_table` con `id` «bigint, clave primaria» y `capacity` «entero positivo obligatorio» en `src/main/resources/db/migration/V1__create_dining_table.sql`. El catálogo se carga como dato preexistente.
- [X] T006 [P] Modelar `dining_table` en `src/main/java/bistro/tables/model/DiningTable.java` con `id` «bigint, clave primaria» y `capacity` «entero positivo obligatorio».
- [X] T007 [P] Modelar la reserva en `src/main/java/bistro/reservations/model/Reservation.java` y sus estados en `src/main/java/bistro/reservations/model/ReservationStatus.java`. Aplicar las reglas del modelo para `reservationCode` «UUID aleatorio, clave primaria, público e inmutable», `customerName` «Texto obligatorio con al menos un carácter distinto de espacio», `customerEmail` «Texto obligatorio, no vacío y con formato de email», `reservationTime` «Instante obligatorio, `timestamptz`. El DTO recibe fecha y hora con desplazamiento explícito.», `partySize` «Entero positivo obligatorio. `BigInteger` en Java y `numeric` en PostgreSQL, con restricciones de integralidad y positividad.», `status` «Texto obligatorio restringido a PENDING, CONFIRMED o REJECTED» y `assignedTableId` «ID numérico opcional que referencia una mesa existente. Presente exactamente cuando el estado es CONFIRMED.». Crear en memoria como `PENDING` y permitir únicamente confirmar con un ID o rechazar con mesa nula desde ese estado.
- [X] T008 Crear `reservation` en `src/main/resources/db/migration/V2__create_reservation.sql` con los tipos y columnas de `specs/001-reservation-lifecycle/data-model.md`. Aplicar clave primaria UUID, campos obligatorios, valor numérico finito, positivo e integral para `party_size`, valores de `status`, clave foránea escalar a `dining_table` y equivalencia entre `CONFIRMED` y mesa asignada. Indexar por mesa y hora las filas confirmadas.
- [X] T009 Definir el repositorio Spring Data por UUID en `src/main/java/bistro/reservations/repository/ReservationRepository.java` para persistir reservas y leerlas por código.
- [X] T010 [P] Implementar `@RestControllerAdvice` en `src/main/java/bistro/ApiExceptionHandler.java` para responder con `ProblemDetail` y `application/problem+json`. Cubrir 400 por validación, cuerpo ausente o JSON ilegible y 500 por fallos técnicos con título y detalle en español y sin detalles internos de persistencia.
- [X] T011 [P] Preparar en `src/test/java/bistro/reservations/ReservationTestDatabase.java` el soporte común de pruebas HTTP con PostgreSQL 17 de Testcontainers, migraciones reales y fixtures SQL aisladas para mesas y reservas.

## Fase 3 — US1 · Solicitar una reserva y conocer su resultado definitivo (P1, MVP)

**Objetivo** · Resolver cada POST válido en la misma solicitud y devolver un código con un estado final persistido.

**Prueba independiente** · Con mesas y ocupaciones conocidas, enviar POST sin credenciales y comprobar el 201, `Location`, el estado público y la asignación en PostgreSQL. Repetir con entradas inválidas, franjas adyacentes o solapadas y dos solicitudes simultáneas por una sola mesa.

### Pruebas de aceptación

- [X] T012 [P] [US1] Escribir en `src/test/java/bistro/reservations/CreateReservationHttpTest.java` la prueba HTTP del POST según `specs/001-reservation-lifecycle/contracts/openapi.yaml`. Verificar el cuerpo exacto, los UUID distintos y `Location` para ambos resultados; comprobar datos guardados y mesa presente solo al confirmar. Cubrir capacidad exacta, catálogo vacío, mesa ocupada con otra libre, todas ocupadas, capacidades que solo alcanzan combinadas, cantidad superior a 32 bits, solape parcial, offsets equivalentes, adyacencia, medianoche y que el rechazo deja libres las mesas. Comprobar 400 en español y persistencia intacta para cada campo omitido o nulo, nombre en blanco, email inválido, hora ilegible o sin offset, JSON ilegible, cantidad cero, negativa, fraccionaria o enviada como cadena y tipos JSON incompatibles.
- [X] T013 [P] [US1] Escribir en `src/test/java/bistro/reservations/ReservationTransactionHttpTest.java` pruebas con conexiones independientes que coordinen dos POST sobre una sola mesa y comprueben que la segunda solicitud espera el bloqueo y lee tras el commit. Verificar dos 201 y exactamente una confirmación, un rechazo y dos códigos. Provocar un fallo de escritura en PostgreSQL y comprobar 500 en español, ausencia de la reserva tras rollback y confirmación en un POST posterior para la misma franja.

### Implementación

- [X] T014 [P] [US1] Crear `lockCandidates(BigInteger partySize)` en `src/main/java/bistro/tables/service/TableCatalog.java`, su implementación en `src/main/java/bistro/tables/service/DatabaseTableCatalog.java` y la consulta en `src/main/java/bistro/tables/repository/DiningTableRepository.java`. Exigir la transacción llamante y materializar solo IDs con capacidad mayor o igual al grupo completo mediante `SELECT … ORDER BY id FOR UPDATE`, esperando los bloqueos y conservándolos hasta el fin de la transacción.
- [X] T015 [P] [US1] Añadir en `src/main/java/bistro/reservations/repository/ReservationRepository.java` una consulta de IDs ocupados para las candidatas y estado `CONFIRMED`. Filtrar inicios estrictamente posteriores al inicio solicitado menos 90 minutos y estrictamente anteriores al fin solicitado.
- [X] T016 [P] [US1] Definir `src/main/java/bistro/reservations/controller/CreateReservationRequest.java` con Bean Validation y mensajes en español, según el modelo «`@NotBlank` en nombre y email, `@Email` en email, `@NotNull` en la hora y `@NotNull @Positive` en la cantidad». Configurar en `src/main/java/bistro/reservations/controller/ReservationRequestJsonConfiguration.java` la lectura estricta de los tipos del DTO, fecha y hora RFC 3339 con desplazamiento explícito y cantidad numérica de valor entero sin truncar fracciones ni limitar a 32 bits.
- [X] T017 [P] [US1] Definir en `src/main/java/bistro/reservations/controller/CreatedReservationResponse.java` y `src/main/java/bistro/reservations/controller/CreatedReservationMapper.java` el DTO y la proyección MapStruct de solo `reservationCode` y `status` de la reserva resuelta.
- [X] T018 [US1] Implementar en `src/main/java/bistro/reservations/service/CreateReservationService.java` la creación de `PENDING` en memoria, el bloqueo de candidatas a través de `TableCatalog` y, si hay candidatas, la consulta de ocupaciones en una segunda sentencia bajo `READ COMMITTED`. Comparar instantes en una franja de 90 minutos, asignar una sola mesa libre o rechazar con mesa nula, persistir solo el resultado final y propagar los fallos técnicos para revertir toda la transacción.
- [X] T019 [US1] Exponer `POST /api/v1/reservations` en `src/main/java/bistro/reservations/controller/CreateReservationController.java`. Validar con `@Valid` antes de invocar al servicio y devolver, después del commit, 201 con el DTO de creación y `Location: /api/v1/reservations/{reservationCode}` para ambos resultados finales.

## Fase 4 — US2 · Consultar una reserva por su código (P2)

**Objetivo** · Recuperar la decisión guardada usando solo el código público.

**Prueba independiente** · Sembrar por SQL una reserva confirmada y otra rechazada. Consultar repetidamente ambos códigos tras añadir capacidad y comprobar 200 con solo el estado original. Comprobar 404 en español para un UUID desconocido y para texto que no representa un UUID, y verificar que los datos y asignaciones siguen intactos.

### Pruebas de aceptación

- [X] T020 [P] [US2] Escribir en `src/test/java/bistro/reservations/GetReservationStatusHttpTest.java` la prueba HTTP del GET según `specs/001-reservation-lifecycle/contracts/openapi.yaml`, con datos sembrados en ambos estados finales y consultas sin credenciales. Cubrir lecturas repetidas tras añadir disponibilidad, cuerpo exacto del 200, 404 para UUID inexistente y código no UUID, y 500 simulado por fallo de lectura. Verificar los errores en español y la ausencia de escrituras.

### Implementación

- [X] T021 [P] [US2] Implementar la lectura por código en `src/main/java/bistro/reservations/service/GetReservationStatusService.java` con `src/main/java/bistro/reservations/service/ReservationNotFoundException.java`. Validar la forma textual completa del UUID antes de convertirlo y tratar el formato inválido y el código inexistente como reserva no encontrada y recuperar la decisión persistida mediante una operación de solo lectura.
- [X] T022 [P] [US2] Definir en `src/main/java/bistro/reservations/controller/ReservationStatusResponse.java` y `src/main/java/bistro/reservations/controller/ReservationStatusMapper.java` el DTO y la proyección MapStruct cuyo único campo es `status`.
- [X] T023 [US2] Incorporar el 404 de reserva no encontrada a `src/main/java/bistro/ApiExceptionHandler.java` con `application/problem+json` y título y detalle en español según el contrato.
- [X] T024 [US2] Exponer `GET /api/v1/reservations/{reservationCode}` en `src/main/java/bistro/reservations/controller/GetReservationStatusController.java`, recibir el código como texto y devolver 200 con el estado mediante el servicio de lectura y el mapper.

## Fase 5 — Verificación transversal

- [X] T025 Ejecutar `./mvnw verify` desde `pom.xml` y reproducir `specs/001-reservation-lifecycle/quickstart.md` desde una base limpia usando `README.md`. Comprobar FR-001 a FR-012 y SC-001 a SC-005 en `specs/001-reservation-lifecycle/spec.md`, incluidos el recorrido POST → `Location` → GET y las invariantes SQL.
- [X] T026 Revisar en `pom.xml`, `src/main/java/bistro/BistroApplication.java`, `src/main/java/bistro/ApiExceptionHandler.java`, `src/main/java/bistro/reservations/controller/CreateReservationController.java` `src/main/java/bistro/reservations/controller/GetReservationStatusController.java` y `src/main/java/bistro/reservations/service/CreateReservationService.java` el artefacto único, la separación de capas y repositorios, el acceso a mesas por `TableCatalog`, el contrato de éxito y error y el idioma conforme a `.specify/memory/constitution.md`. Aplicar `clase-NN-slug` a los commits que se realicen.

## Fase 6 — US1 · Validar el máximo al solicitar una reserva (P1, MVP de FR-013)

**Objetivo** · Aceptar de 1 a 12 comensales y devolver 400 en español para cantidades mayores antes de crear la reserva o consultar disponibilidad.

**Prueba independiente** · Enviar un POST de 12 con y sin mesa apta y obtener 201 con el resultado correspondiente. Con una mesa libre de 13 plazas, enviar un POST de 13 y comprobar 400 sin código, encabezado `Location`, fila nueva ni llamada al servicio de creación.

### Pruebas de aceptación

- [X] T027 [P] [US1] Actualizar las pruebas de `POST /api/v1/reservations` en `src/test/java/bistro/reservations/CreateReservationHttpTest.java` según `specs/001-reservation-lifecycle/contracts/openapi.yaml`. Comprobar 201 y asignación para 1 y 12 con mesa apta, y 201 con `REJECTED` y mesa nula para 12 sin mesa apta y para 6 con dos mesas de cuatro plazas. Con una mesa libre de 13 plazas, exigir 400 para 13, `13.0`, `2147483648` y el entero largo que cubre la prueba actual. Sustituir las expectativas de persistencia de esos enteros por `application/problem+json` con título «Solicitud inválida» y detalle «La cantidad de comensales no puede superar las 12 personas.», sin código ni `Location` y con las filas y asignaciones intactas. Conservar la aceptación de números de valor entero como `12.0` y `1.2e1`.
- [X] T028 [P] [US1] Crear `src/test/java/bistro/reservations/CreateReservationValidationMvcTest.java` como prueba Spring MVC enfocada en `CreateReservationController`, con la validación y el advice reales y un doble de `CreateReservationService`. Enviar un cuerpo válido salvo por `partySize: 13`, comprobar HTTP 400 con el detalle del contrato y verificar cero interacciones con el servicio de creación, punto de entrada a la consulta de disponibilidad.

### Implementación

- [X] T029 [US1] Aplicar a `partySize` de `src/main/java/bistro/reservations/controller/CreateReservationRequest.java` la regla del modelo «Entero positivo obligatorio. La solicitud HTTP exige de 1 a 12 inclusive. Se conserva `BigInteger` en Java y `numeric` en PostgreSQL para admitir la lectura de registros históricos.». Declarar `@NotNull @Positive @Max(12)` con el mensaje «La cantidad de comensales no puede superar las 12 personas.» para el máximo. Usar el flujo existente de `@Valid` en `src/main/java/bistro/reservations/controller/CreateReservationController.java` y de `ProblemDetail` en `src/main/java/bistro/ApiExceptionHandler.java`.

## Fase 7 — US2 · Consultar reservas anteriores al máximo (P2)

**Objetivo** · Mantener la consulta por código de reservas históricas de más de 12 comensales.

**Prueba independiente** · Sembrar una reserva histórica de 13 por SQL y consultar su código. El GET devuelve 200 con el estado guardado y conserva su asignación.

### Prueba de compatibilidad

- [X] T030 [P] [US2] Añadir un caso en `src/test/java/bistro/reservations/GetReservationStatusHttpTest.java` que siembre por SQL una reserva de 13 comensales en estado `CONFIRMED` con una mesa de capacidad suficiente. Consultar el código mediante `GET /api/v1/reservations/{reservationCode}` y verificar 200 con el cuerpo exacto `{"status":"CONFIRMED"}` y las filas y asignaciones intactas. La fixture permite ejecutar el caso con la implementación actual del GET.

## Fase 8 — Verificación transversal de FR-013

- [X] T031 Ejecutar `./mvnw verify` desde `pom.xml` y reproducir el escenario del máximo en `specs/001-reservation-lifecycle/quickstart.md` con una base desechable. Revisar 201 para ambos resultados de un grupo de 12 y 400 para 13 frente a `specs/001-reservation-lifecycle/contracts/openapi.yaml`. Confirmar la cobertura del GET histórico, las invariantes SQL y el cumplimiento de `.specify/memory/constitution.md` versión 1.3.0, incluida la validación antes del servicio y la interfaz pública en `src/main/java/bistro/tables/service/TableCatalog.java`.

## Dependencias y orden de ejecución

```text
Base completada ┬→ US1 (T027 y T028 → T029) ─┬→ T031
                └→ US2 (T030) ──────────────┘
```

- La preparación y los fundamentos de las fases 1 y 2 ya habilitan las dos historias.
- Ejecutar T027 y T028 antes de T029 para observar el fallo esperado de FR-013. Repetirlas después de aplicar el límite. T030 puede pasar desde el inicio porque usa datos sembrados.
- Ejecutar T031 cuando ambas historias pasen sus pruebas.

## Ejemplos de trabajo en paralelo

- **US1** · Preparar T027 en la suite HTTP con PostgreSQL y T028 en la prueba MVC en paralelo.
- **US2** · Preparar T030 mientras se trabaja en US1, en un archivo de prueba distinto. Si se ejecutan simultáneamente los casos HTTP de T027 y T030, usar bases de pruebas separadas para sus fixtures SQL.

## Estrategia de implementación

1. Entregar el límite de entrada como MVP de US1. Validar el 400 para 13 y los dos resultados de negocio que siguen disponibles para 12.
2. Verificar US2 de forma independiente con la reserva histórica sembrada.
3. Ejecutar la verificación transversal y el recorrido POST → `Location` → GET para una solicitud válida.

## Phase 9: Convergence

- [X] T032 **CRITICAL** Reserva `bistro.tables.controller` y `bistro.notifications` mediante declaraciones de paquete en `src/main/java/bistro/`. Refleja las capas `controller`, `service`, `repository` y `model` previstas para el futuro módulo de notificaciones y deja la reserva de paquetes como única entrega de esta tarea, según la Constitución I–II, la estructura del plan y T026 (contradicts)
- [X] T033 Conserva la precisión de las horas aceptadas por HTTP al comparar y persistir los intervalos de 90 minutos. Añade una prueba en `src/test/java/bistro/reservations/CreateReservationHttpTest.java` con inicios `19:00:00.0000009Z` y `20:30:00.0000001Z` en la misma fecha y la única mesa apta, y verifica rechazo para el solape y confirmación para la adyacencia exacta. Mantén la consulta y la ocupación de las reservas existentes, según FR-005, FR-008, SC-003 y T016 (partial)
- [X] T034 Devuelve el detalle «La cantidad de comensales no puede superar las 12 personas.» para cantidades JSON enteras sobre el máximo que superan el límite numérico del lector, también en notación exponencial como `1e131072`. Mantén el procesamiento numérico acotado y comprueba en `src/test/java/bistro/reservations/CreateReservationHttpTest.java` el 400 en español, la ausencia de `Location` y que las reservas y asignaciones siguen intactas, según FR-013, el plan de validación y T029 (partial)

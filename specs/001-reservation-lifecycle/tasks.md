---
description: "Tareas para aplicar la disponibilidad por instante exacto a las reservas de bistro"
---

# Tareas del ciclo de vida de una reserva

**Diseño** · [Plan](plan.md), [especificación](spec.md), [investigación](research.md), [modelo de datos](data-model.md), [contrato HTTP](contracts/openapi.yaml) y [guía de validación](quickstart.md).

Este listado cubre el incremento de disponibilidad por instante y conservación de reservas históricas (FR-005, FR-008 y FR-014). El historial de la entrega anterior está en Git, en la versión de este archivo del commit `22f9b93`.

**Formato** · Cada tarea incluye un ID y rutas desde la raíz. `[US1]` y `[US2]` identifican las historias. `[P]` indica trabajo que se puede preparar en paralelo según las dependencias de abajo.

## Fase 1 — Preparación

- [X] T001 Preparar Java 25 y comprobar la versión del Maven Wrapper con `./mvnw --version` según `pom.xml` y `.mvn/wrapper/maven-wrapper.properties`. Preparar una base exclusiva de pruebas PostgreSQL 17 mediante Testcontainers o con las variables `BISTRO_TEST_DATABASE_*` que admite `src/test/java/bistro/reservations/ReservationTestDatabase.java`. Seguir `README.md` y `specs/001-reservation-lifecycle/quickstart.md`.

## Fase 2 — Fundamentos compartidos

- [X] T002 Fijar la referencia de compatibilidad antes del cambio. Revisar `src/main/resources/db/migration/V1__create_dining_table.sql`, `src/main/resources/db/migration/V2__create_reservation.sql` y `src/main/resources/db/migration/V3__preserve_reservation_time_precision.sql`, incluido el índice parcial `reservation_occupied_table_time_idx` y el resto por defecto `''`. Conservar esas migraciones y el esquema. Verificar en `src/main/java/bistro/reservations/model/Reservation.java`, `src/main/java/bistro/reservations/model/ReservationStart.java` y `src/main/java/bistro/reservations/controller/CreateReservationRequest.java` las reglas vigentes: `reservationCode` «UUID aleatorio, clave primaria, público e inmutable», `customerName` «Texto obligatorio con al menos un carácter distinto de espacio», `customerEmail` «Texto obligatorio, no vacío y con formato de email», `reservationTime` «Instante obligatorio. `timestamptz` conserva hasta seis decimales y la columna de texto guarda los dígitos posteriores, sin ceros finales. La cadena vacía representa una fracción adicional de cero, también para las filas previas a la migración. El DTO recibe fecha y hora con desplazamiento explícito.», `partySize` «Entero positivo obligatorio. La solicitud HTTP exige de 1 a 12 inclusive. Se conserva `BigInteger` en Java y `numeric` en PostgreSQL para admitir la lectura de registros históricos.», `status` «Texto obligatorio restringido a PENDING, CONFIRMED o REJECTED» y `assignedTableId` «ID numérico opcional que referencia una mesa existente. Presente exactamente cuando el estado es CONFIRMED.». Comprobar en `src/main/java/bistro/tables/model/DiningTable.java` las reglas de `id` «bigint, clave primaria» y `capacity` «entero positivo obligatorio». Ejecutar `./mvnw -Dtest=CreateReservationValidationMvcTest,GetReservationStatusHttpTest test` para comprobar la validación y la lectura existentes.

## Fase 3 — US1 · Solicitar una reserva y conocer su resultado definitivo (P1, MVP)

**Objetivo** · Resolver cada POST válido en la misma solicitud y excluir una mesa apta únicamente si existe una reserva confirmada para ese mismo instante.

**Prueba independiente** · Con una sola mesa apta, confirmar una solicitud y comprobar que el mismo instante, incluso con un desplazamiento equivalente que cruza medianoche, devuelve 201 con `REJECTED`. Confirmar solicitudes para las 19:01 del mismo día, las 19:00 del día siguiente y dos instantes distintos dentro del mismo microsegundo. Verificar códigos, `Location`, estados y asignaciones en PostgreSQL. Para dos POST simultáneos sobre la misma mesa y el mismo instante, comprobar dos resultados 201, exactamente una confirmación y un rechazo.

### Pruebas de aceptación

- [X] T003 [P] [US1] Actualizar en `src/test/java/bistro/reservations/CreateReservationHttpTest.java` los casos de solape, adyacencia y medianoche al contrato de `specs/001-reservation-lifecycle/contracts/openapi.yaml`. Con una sola mesa, comprobar conflicto para el mismo instante y para `19:00-05:00` frente a `00:00Z` del día siguiente. Comprobar en casos independientes reutilización de la misma mesa a las 19:01 de la misma fecha y a las 19:00 del día siguiente, y confirmación a las 00:30 cuando ya se confirmó a las 23:30 del día anterior en el mismo desplazamiento. Confirmar `19:00:00.0000009Z` y `19:00:00.0000001Z` en la misma fecha y rechazar `19:00:00.0000009000Z` como equivalente al primero. Afirmar los restos canónicos persistidos y la asignación solo en las confirmadas. Ajustar el caso de offsets extremos para distinguir un resto diferente de una representación equivalente. Ejecutar la clase antes del cambio de producción y comprobar que fallan las expectativas que sustituyen la regla de 90 minutos.

### Implementación

- [X] T004 [US1] Reemplazar en `src/main/java/bistro/reservations/repository/ReservationRepository.java` los límites `after` y `before` de `findOccupiedTableIds` por parámetros para el microsegundo UTC y el resto canónico del instante solicitado. Devolver los IDs distintos de las mesas candidatas con `status = 'CONFIRMED'`, `reservation_time = :reservationTime` y `reservation_time_remainder = :remainder`. Reutilizar el índice parcial por mesa y microsegundo y filtrar el resto por igualdad.
- [X] T005 [US1] Quitar `SLOT_DURATION`, el import de `Duration` y el cálculo de la franja en `src/main/java/bistro/reservations/service/CreateReservationService.java`. Pasar `ReservationStart.microsecond()` y `ReservationStart.remainder()` a `findOccupiedTableIds`. Mantener `READ COMMITTED`, el bloqueo por ID ascendente mediante `src/main/java/bistro/tables/service/TableCatalog.java` y la consulta en una segunda sentencia tras obtener los bloqueos. Si el catálogo devuelve cero candidatas, omitir la consulta de ocupación. Asignar una sola mesa libre o rechazar con mesa nula, guardar la decisión definitiva y propagar fallos técnicos para que la transacción revierta.
- [X] T006 [US1] Ejecutar `./mvnw -Dtest=CreateReservationHttpTest,CreateReservationValidationMvcTest,ReservationTransactionHttpTest test` contra PostgreSQL de pruebas. Confirmar el contrato de POST en `src/test/java/bistro/reservations/CreateReservationHttpTest.java`, el máximo de 12 antes de llegar al servicio en `src/test/java/bistro/reservations/CreateReservationValidationMvcTest.java`, y la espera por bloqueo, visibilidad tras commit, exclusión mutua al mismo instante y liberación de la mesa tras rollback en `src/test/java/bistro/reservations/ReservationTransactionHttpTest.java`.

**Punto de control** · El POST decide con la nueva regla y conserva la validación de entrada y los dos resultados HTTP 201.

## Fase 4 — US2 · Consultar una reserva por su código (P2)

**Objetivo** · Recuperar por el código original la decisión de cualquier reserva existente y conservar su instante y asignación al aplicar la nueva regla.

**Prueba independiente** · Sembrar reservas confirmadas y rechazadas por SQL. Consultar los códigos repetidamente y comprobar 200 con solo el estado guardado y 404 en español para un código desconocido. Con una única mesa y una confirmada sembrada con resto `''`, comprobar que bloquea el instante registrado y permite reutilizar la mesa en otro instante. Comparar la fila anterior y posterior, incluido el resto, y repetir la consulta al código original.

### Pruebas de compatibilidad

- [X] T007 [P] [US2] Añadir en `src/test/java/bistro/reservations/GetReservationStatusHttpTest.java` el escenario 5 de US2. Usar la fixture SQL existente de `src/test/java/bistro/reservations/ReservationTestDatabase.java`, que omite el resto y deja que se aplique el valor por defecto. Guardar el código, los dos componentes de la fecha y hora, el estado y la mesa de la fila. Consultar el código original y comprobar el cuerpo exacto `{"status":"CONFIRMED"}`. Para el instante original esperar 201 con `REJECTED` y mesa nula, y para un instante distinto dentro de la antigua franja esperar 201 con `CONFIRMED` y la misma mesa. Repetir el GET y comparar los valores originales de la fila. Conservar los casos existentes de ambos estados, 404 y reserva histórica de 13 comensales. Ejecutar `./mvnw -Dtest=GetReservationStatusHttpTest test` antes de T004 para comprobar el fallo esperado en el POST del instante distinto y repetir después de T005. El GET existente permite consultar las fixtures sin crear reservas por HTTP.

**Punto de control** · El GET sigue devolviendo la decisión guardada, incluidas las reservas históricas, y el POST tiene en cuenta su asignación en el instante original.

## Fase 5 — Verificación transversal

- [X] T008 Ejecutar `./mvnw verify` desde `pom.xml` y reproducir `specs/001-reservation-lifecycle/quickstart.md` en una base desechable. Contrastar con `specs/001-reservation-lifecycle/contracts/openapi.yaml` los dos POST 201 con `Location`, los GET 200, los errores 400 y 404 en español y el 500 de las pruebas por fallo técnico. Verificar en PostgreSQL que la relación entre estado y mesa se mantiene, que ninguna pareja confirmada comparte mesa e instante y que la fila histórica conserva fecha, hora, código, estado y mesa.
- [X] T009 Revisar el diff de `src/main/java/bistro/reservations/repository/ReservationRepository.java` y `src/main/java/bistro/reservations/service/CreateReservationService.java` frente a `.specify/memory/constitution.md` y `specs/001-reservation-lifecycle/plan.md`. Confirmar que se reutilizan el esquema, las migraciones, la API, `TableCatalog` y los pasos de arranque de `README.md`. Si varias instancias escriben en la misma base, coordinar su cambio al publicar la versión para que todos los POST apliquen la misma regla.

## Dependencias y orden de ejecución

```text
T001 → T002 → T003 y preparación de T007
T003 y primera ejecución de T007 → T004 → T005 → T006 y segunda ejecución de T007
T006 y T007 aprobadas → T008 → T009
```

- **US1** · Después de la preparación y los fundamentos, comprobar el fallo esperado de sus nuevas expectativas. Aplicar primero la consulta y actualizar su llamador inmediatamente después. Validar el POST y la transacción como incremento independiente.
- **US2** · La fixture SQL, el GET y la redacción de la prueba se pueden usar desde que termina T002. La comprobación de reutilización de la mesa por un POST depende de T004 y T005.
- **Verificación transversal** · Comienza cuando pasan las comprobaciones de ambas historias.

## Ejemplos de trabajo en paralelo

- **US1** · Preparar T003 en `CreateReservationHttpTest.java` mientras se prepara T007 en `GetReservationStatusHttpTest.java`.
- **US2** · Después de T005, ejecutar T007 mientras se valida US1 con T006. Usar bases de pruebas separadas si coinciden las ejecuciones, porque las fixtures vacían las tablas antes de cada caso.

## Estrategia de implementación

1. Completar la preparación y los fundamentos. Verificar que los nuevos casos que contradicen la regla anterior fallen antes del cambio.
2. Entregar US1 como MVP con asignación por instante, las garantías transaccionales y el POST validado.
3. Completar US2 con reservas sembradas y verificar que la consulta y las asignaciones históricas sobreviven al cambio.
4. Cerrar con la suite completa, la guía HTTP y la revisión de compatibilidad.

## Incremento — fecha y hora local a precisión de minuto

Las tareas T001 a T009 registran la entrega anterior. Este incremento aplica la revisión vigente de la spec 001.

- [X] T010 Revisar los requisitos y la constitución, comprobar Java y Maven, y verificar los archivos de exclusión del proyecto.
- [X] T011 [US1] Adaptar las pruebas HTTP y MVC y las fixtures de `src/test/java/bistro/reservations/` al formato local y al rango de comensales. Verificar el fallo esperado antes de implementar.
- [X] T012 [US2] Cubrir en `src/test/java/bistro/reservations/` la actualización desde V3, la consulta de reservas históricas, las coincidencias por minuto y la reversión de una migración inválida.
- [X] T013 Añadir V4 en `src/main/resources/db/migration/` y configurar Flyway y el acceso JDBC directo en `src/main/resources/application.yaml`.
- [X] T014 [US1] Usar `LocalDateTime` e `Integer` en `src/main/java/bistro/reservations/`. Comparar minutos en el repositorio y adaptar el argumento de `TableCatalog` en `src/main/java/bistro/tables/`. Conservar las validaciones y el bloqueo transaccional.
- [X] T015 Actualizar `README.md`, contrastar la API con `contracts/openapi.yaml`, ejecutar `./mvnw verify` sobre PostgreSQL y revisar el diff frente a la constitución.

T010 precede a las pruebas T011 y T012. Después se aplican T013 y T014 y se ejecuta T015.

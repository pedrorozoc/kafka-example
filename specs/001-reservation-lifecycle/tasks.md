---
description: "Tareas de implementación para el ciclo de vida de una reserva"
---

# Tareas del ciclo de vida de una reserva

**Diseño** · [Plan](plan.md), [especificación](spec.md), [investigación](research.md), [modelo](data-model.md), [contrato HTTP](contracts/openapi.yaml) y [guía de validación](quickstart.md).

**Formato** · `[ID] [P?] [USn?] Descripción con ruta desde la raíz`. `[P]` identifica trabajo que puede coincidir con otra tarea indicada en la sección de dependencias, una vez resueltos los prerrequisitos de ambas. `[USn]` identifica la historia.

## Fase 1 — Preparación

- [ ] T001 Configurar Java 21 y un único JAR ejecutable Spring Boot 4.2.0 en `pom.xml`, con Spring MVC, Data JPA, Bean Validation, PostgreSQL JDBC, Flyway y su módulo PostgreSQL, MapStruct 1.6.3 con procesador de la misma versión y componentes Spring, JUnit Jupiter, soporte de pruebas HTTP de Spring Boot y Testcontainers PostgreSQL.
- [ ] T002 [P] Incorporar Maven Wrapper 3.9.x en `mvnw` y `.mvn/wrapper/maven-wrapper.properties`, con el script ejecutable desde la raíz del repositorio.
- [ ] T003 [P] Crear el punto de entrada en `src/main/java/bistro/BistroApplication.java` y configurar el datasource por entorno, Flyway y la validación del esquema por JPA en `src/main/resources/application.yaml`.
- [ ] T004 Documentar en `README.md` el arranque desde una copia nueva con Java 21, Maven Wrapper y PostgreSQL 17, las variables de conexión, la migración al arrancar y cómo cargar la mesa de cuatro plazas de `specs/001-reservation-lifecycle/quickstart.md` exclusivamente en una base de validación.

## Fase 2 — Fundamentos compartidos

- [ ] T005 [P] Crear `dining_table` con `id` «bigint, clave primaria» y `capacity` «entero positivo obligatorio» en `src/main/resources/db/migration/V1__create_dining_table.sql`. El catálogo se carga como dato preexistente.
- [ ] T006 [P] Modelar `dining_table` en `src/main/java/bistro/tables/model/DiningTable.java` con `id` «bigint, clave primaria» y `capacity` «entero positivo obligatorio».
- [ ] T007 [P] Modelar la reserva en `src/main/java/bistro/reservations/model/Reservation.java` y sus estados en `src/main/java/bistro/reservations/model/ReservationStatus.java`. Aplicar las reglas del modelo para `reservationCode` «UUID aleatorio, clave primaria, público e inmutable», `customerName` «Texto obligatorio con al menos un carácter distinto de espacio», `customerEmail` «Texto obligatorio, no vacío y con formato de email», `reservationTime` «Instante obligatorio, `timestamptz`. El DTO recibe fecha y hora con desplazamiento explícito.», `partySize` «Entero positivo obligatorio. `BigInteger` en Java y `numeric` en PostgreSQL, con restricciones de integralidad y positividad.», `status` «Texto obligatorio restringido a PENDING, CONFIRMED o REJECTED» y `assignedTableId` «ID numérico opcional que referencia una mesa existente. Presente exactamente cuando el estado es CONFIRMED.». Crear en memoria como `PENDING` y permitir únicamente confirmar con un ID o rechazar con mesa nula desde ese estado.
- [ ] T008 Crear `reservation` en `src/main/resources/db/migration/V2__create_reservation.sql` con los tipos y columnas de `specs/001-reservation-lifecycle/data-model.md`. Aplicar clave primaria UUID, campos obligatorios, valor numérico finito, positivo e integral para `party_size`, valores de `status`, clave foránea escalar a `dining_table` y equivalencia entre `CONFIRMED` y mesa asignada. Indexar por mesa y hora las filas confirmadas.
- [ ] T009 Definir el repositorio Spring Data por UUID en `src/main/java/bistro/reservations/repository/ReservationRepository.java` para persistir reservas y leerlas por código.
- [ ] T010 [P] Implementar `@RestControllerAdvice` en `src/main/java/bistro/ApiExceptionHandler.java` para responder con `ProblemDetail` y `application/problem+json`. Cubrir 400 por validación, cuerpo ausente o JSON ilegible y 500 por fallos técnicos con título y detalle en español y sin detalles internos de persistencia.
- [ ] T011 [P] Preparar en `src/test/java/bistro/reservations/ReservationTestDatabase.java` el soporte común de pruebas HTTP con PostgreSQL 17 de Testcontainers, migraciones reales y fixtures SQL aisladas para mesas y reservas.

## Fase 3 — US1 · Solicitar una reserva y conocer su resultado definitivo (P1, MVP)

**Objetivo** · Resolver cada POST válido en la misma solicitud y devolver un código con un estado final persistido.

**Prueba independiente** · Con mesas y ocupaciones conocidas, enviar POST sin credenciales y comprobar el 201, `Location`, el estado público y la asignación en PostgreSQL. Repetir con entradas inválidas, franjas adyacentes o solapadas y dos solicitudes simultáneas por una sola mesa.

### Pruebas de aceptación

- [ ] T012 [P] [US1] Escribir en `src/test/java/bistro/reservations/CreateReservationHttpTest.java` la prueba HTTP del POST según `specs/001-reservation-lifecycle/contracts/openapi.yaml`. Verificar el cuerpo exacto, los UUID distintos y `Location` para ambos resultados; comprobar datos guardados y mesa presente solo al confirmar. Cubrir capacidad exacta, catálogo vacío, mesa ocupada con otra libre, todas ocupadas, capacidades que solo alcanzan combinadas, cantidad superior a 32 bits, solape parcial, offsets equivalentes, adyacencia, medianoche y que el rechazo deja libres las mesas. Comprobar 400 en español y persistencia intacta para cada campo omitido o nulo, nombre en blanco, email inválido, hora ilegible o sin offset, JSON ilegible, cantidad cero, negativa, fraccionaria o enviada como cadena y tipos JSON incompatibles.
- [ ] T013 [P] [US1] Escribir en `src/test/java/bistro/reservations/ReservationTransactionHttpTest.java` pruebas con conexiones independientes que coordinen dos POST sobre una sola mesa y comprueben que la segunda solicitud espera el bloqueo y lee tras el commit. Verificar dos 201 y exactamente una confirmación, un rechazo y dos códigos. Provocar un fallo de escritura en PostgreSQL y comprobar 500 en español, ausencia de la reserva tras rollback y confirmación en un POST posterior para la misma franja.

### Implementación

- [ ] T014 [P] [US1] Crear `lockCandidates(BigInteger partySize)` en `src/main/java/bistro/tables/service/TableCatalog.java`, su implementación en `src/main/java/bistro/tables/service/DatabaseTableCatalog.java` y la consulta en `src/main/java/bistro/tables/repository/DiningTableRepository.java`. Exigir la transacción llamante y materializar solo IDs con capacidad mayor o igual al grupo completo mediante `SELECT … ORDER BY id FOR UPDATE`, esperando los bloqueos y conservándolos hasta el fin de la transacción.
- [ ] T015 [P] [US1] Añadir en `src/main/java/bistro/reservations/repository/ReservationRepository.java` una consulta de IDs ocupados para las candidatas y estado `CONFIRMED`. Filtrar inicios estrictamente posteriores al inicio solicitado menos 90 minutos y estrictamente anteriores al fin solicitado.
- [ ] T016 [P] [US1] Definir `src/main/java/bistro/reservations/controller/CreateReservationRequest.java` con Bean Validation y mensajes en español, según el modelo «`@NotBlank` en nombre y email, `@Email` en email, `@NotNull` en la hora y `@NotNull @Positive` en la cantidad». Configurar en `src/main/java/bistro/reservations/controller/ReservationRequestJsonConfiguration.java` la lectura estricta de los tipos del DTO, fecha y hora RFC 3339 con desplazamiento explícito y cantidad numérica de valor entero sin truncar fracciones ni limitar a 32 bits.
- [ ] T017 [P] [US1] Definir en `src/main/java/bistro/reservations/controller/CreatedReservationResponse.java` y `src/main/java/bistro/reservations/controller/CreatedReservationMapper.java` el DTO y la proyección MapStruct de solo `reservationCode` y `status` de la reserva resuelta.
- [ ] T018 [US1] Implementar en `src/main/java/bistro/reservations/service/CreateReservationService.java` la creación de `PENDING` en memoria, el bloqueo de candidatas a través de `TableCatalog` y, si hay candidatas, la consulta de ocupaciones en una segunda sentencia bajo `READ COMMITTED`. Comparar instantes en una franja de 90 minutos, asignar una sola mesa libre o rechazar con mesa nula, persistir solo el resultado final y propagar los fallos técnicos para revertir toda la transacción.
- [ ] T019 [US1] Exponer `POST /api/v1/reservations` en `src/main/java/bistro/reservations/controller/CreateReservationController.java`. Validar con `@Valid` antes de invocar al servicio y devolver, después del commit, 201 con el DTO de creación y `Location: /api/v1/reservations/{reservationCode}` para ambos resultados finales.

## Fase 4 — US2 · Consultar una reserva por su código (P2)

**Objetivo** · Recuperar la decisión guardada usando solo el código público.

**Prueba independiente** · Sembrar por SQL una reserva confirmada y otra rechazada. Consultar repetidamente ambos códigos tras añadir capacidad y comprobar 200 con solo el estado original. Comprobar 404 en español para un UUID desconocido y para texto que no representa un UUID, y verificar que los datos y asignaciones siguen intactos.

### Pruebas de aceptación

- [ ] T020 [P] [US2] Escribir en `src/test/java/bistro/reservations/GetReservationStatusHttpTest.java` la prueba HTTP del GET según `specs/001-reservation-lifecycle/contracts/openapi.yaml`, con datos sembrados en ambos estados finales y consultas sin credenciales. Cubrir lecturas repetidas tras añadir disponibilidad, cuerpo exacto del 200, 404 para UUID inexistente y código no UUID, y 500 simulado por fallo de lectura. Verificar los errores en español y la ausencia de escrituras.

### Implementación

- [ ] T021 [P] [US2] Implementar la lectura por código en `src/main/java/bistro/reservations/service/GetReservationStatusService.java` con `src/main/java/bistro/reservations/service/ReservationNotFoundException.java`. Validar la forma textual completa del UUID antes de convertirlo y tratar el formato inválido y el código inexistente como reserva no encontrada y recuperar la decisión persistida mediante una operación de solo lectura.
- [ ] T022 [P] [US2] Definir en `src/main/java/bistro/reservations/controller/ReservationStatusResponse.java` y `src/main/java/bistro/reservations/controller/ReservationStatusMapper.java` el DTO y la proyección MapStruct cuyo único campo es `status`.
- [ ] T023 [US2] Incorporar el 404 de reserva no encontrada a `src/main/java/bistro/ApiExceptionHandler.java` con `application/problem+json` y título y detalle en español según el contrato.
- [ ] T024 [US2] Exponer `GET /api/v1/reservations/{reservationCode}` en `src/main/java/bistro/reservations/controller/GetReservationStatusController.java`, recibir el código como texto y devolver 200 con el estado mediante el servicio de lectura y el mapper.

## Fase 5 — Verificación transversal

- [ ] T025 Ejecutar `./mvnw verify` desde `pom.xml` y reproducir `specs/001-reservation-lifecycle/quickstart.md` desde una base limpia usando `README.md`. Comprobar FR-001 a FR-012 y SC-001 a SC-005 en `specs/001-reservation-lifecycle/spec.md`, incluidos el recorrido POST → `Location` → GET y las invariantes SQL.
- [ ] T026 Revisar en `pom.xml`, `src/main/java/bistro/BistroApplication.java`, `src/main/java/bistro/ApiExceptionHandler.java`, `src/main/java/bistro/reservations/controller/CreateReservationController.java` `src/main/java/bistro/reservations/controller/GetReservationStatusController.java` y `src/main/java/bistro/reservations/service/CreateReservationService.java` el artefacto único, la separación de capas y repositorios, el acceso a mesas por `TableCatalog`, el contrato de éxito y error y el idioma conforme a `.specify/memory/constitution.md`. Aplicar `clase-NN-slug` a los commits que se realicen.

## Dependencias y orden de ejecución

```text
Preparación → Fundamentos ┬→ US1 (POST) ─┬→ Verificación transversal
                          └→ US2 (GET) ──┘
```

- T001–T003 habilitan T004. En fundamentos, T005 y T007 habilitan T008, T007 habilita T009 y el esquema de T005 y T008 habilita las fixtures de T011. T010 puede avanzar en paralelo con el esquema y los modelos.
- US1 y US2 dependen de los fundamentos. US2 usa datos sembrados, por lo que puede verificarse de forma independiente del POST.
- Escribir y ejecutar las pruebas de cada historia antes de su implementación para observar el fallo inicial. En US1, T014 y T015 habilitan T018, y T016–T018 habilitan T019. En US2, T021 habilita T023 y T021–T023 habilitan T024. T025 y T026 se ejecutan con ambas historias listas.

## Ejemplos de trabajo en paralelo

- **US1** · Escribir T012 y T013 en archivos distintos usando las mismas fixtures de base. Después del fallo inicial, T014, T015, T016 y T017 pueden avanzar en paralelo.
- **US2** · Escribir T020 en paralelo con T012 y T013. Tras observar el fallo inicial de T020, desarrollar en paralelo T021 para la lectura y T022 para la proyección HTTP. Integrar con T023 y T024.

## Estrategia de implementación

1. Completar preparación, fundamentos y US1 como MVP. Validar por POST los dos resultados finales, la persistencia y la exclusión de solapes. La ruta indicada en `Location` queda disponible al incorporar US2.
2. Completar US2 y validar la consulta independientemente con sus fixtures. Comprobar después el recorrido completo usando los códigos devueltos por el POST.
3. Completar la verificación transversal antes de dar por entregado el ciclo de reserva.

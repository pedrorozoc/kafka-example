# Implementation Plan — Ciclo de vida de una reserva

**Rama Git** `master` · **Feature Spec Kit** `001-reservation-lifecycle` · **Fecha** 2026-11-22 · [Especificación](spec.md)

## Summary

Cada reserva tiene una sola fecha y hora de llegada. Una mesa con capacidad suficiente queda excluida solo si tiene una reserva confirmada para el mismo instante (FR-005 y FR-008). El servicio entregará al repositorio el instante canónico completo para comparar sus dos componentes por igualdad. El diseño reutiliza el bloqueo transaccional, el contrato HTTP y las columnas existentes. Las reservas ya registradas conservan su instante, código, estado y mesa (FR-014).

## Technical Context

| Aspecto | Elección |
| --- | --- |
| Aplicación | Un JAR ejecutable en JVM, Java 25, Maven Wrapper 3.9.x, Spring Boot 4.2.0 |
| Dependencias | Spring MVC, Data JPA, Bean Validation, MapStruct 1.6.3, driver JDBC PostgreSQL y Flyway con soporte PostgreSQL |
| Datos | PostgreSQL 17, catálogo de mesas preexistente y reservas persistentes |
| Pruebas | JUnit Jupiter, pruebas HTTP con Spring Boot y transacciones independientes contra PostgreSQL mediante Testcontainers o una base de pruebas configurada |
| Plataforma y alcance | Servicio HTTP para un restaurante. Las dos operaciones públicas funcionan sin cuenta. Cada POST válido devuelve la decisión definitiva en la misma solicitud. |
| Base del proyecto | `ReservationStart` conserva el instante normalizado a UTC y los dígitos submicrosegundo. El cambio se concentra en `CreateReservationService`, `ReservationRepository` y las pruebas de reservas. |
| Restricciones de integridad | Una confirmación como máximo por mesa e instante. Las filas previas siguen siendo consultables y cuentan para la disponibilidad en el instante almacenado. |
| Objetivos de rendimiento | La especificación acota el trabajo a la corrección funcional de la disponibilidad y la concurrencia. |

## Constitution Check

La revisión de entrada y la revisión posterior a la fase 1 cumplen los seis principios de la [constitución 1.3.0](../../.specify/memory/constitution.md).

| Principio | Verificación de entrada | Evidencia en el diseño |
| --- | --- | --- |
| I. Capas | Usar exactamente `controller`, `service`, `repository` y `model` dentro de cada módulo | `controller` contiene adaptadores HTTP, DTOs y mappers MapStruct. `service` decide y orquesta. `repository` contiene interfaces Spring Data. `model` contiene entidades JPA y enums. |
| II. Monolito modular | Un artefacto y límites por dominio | `reservations` solicita los IDs candidatos por la interfaz pública de `tables`. Cada módulo accede a su propio repositorio. Notificaciones queda fuera de la feature. |
| III. HTTP y mapeo | Dos rutas versionadas con DTOs | [Contrato](contracts/openapi.yaml) con DTOs de entrada, creación y consulta. MapStruct proyecta entidades a respuestas que contienen solo campos públicos. |
| IV. Validación y errores | Validación antes del servicio y tratamiento global | El controlador conserva `@Valid`, el lector de fecha y hora y las restricciones de entrada, incluido `@Max(12)`. El tratamiento global conserva los ProblemDetail en español. |
| V. Idioma | Identificadores en inglés, textos en español | Campos y estados en inglés en el contrato. Títulos, detalles, mensajes de excepción, logs y comentarios en español. |
| VI. Dependencias entre módulos | Encapsular dependencias volátiles detrás de una interfaz del proveedor | `tables.service.TableCatalog` es la interfaz pública. `DatabaseTableCatalog` tiene visibilidad de paquete y encapsula el mecanismo de persistencia y bloqueo. |

La revisión de implementación comprobará el README de arranque y la convención de commits `clase-NN-slug`.

## Project Structure

Los artefactos de la feature son [especificación](spec.md), [decisiones y alternativas](research.md), [modelo](data-model.md), [contrato HTTP](contracts/openapi.yaml) y [guía de validación](quickstart.md).

Estructura del backend desde la raíz del repositorio

```text
pom.xml
mvnw
.mvn/wrapper/
README.md
src/main/java/bistro/
  BistroApplication.java
  ApiExceptionHandler.java
  reservations/{controller,service,repository,model}/
  tables/{controller,service,repository,model}/
src/main/resources/
  application.yaml
  db/migration/
src/test/java/bistro/reservations/
```

El manejador global vive en el paquete raíz de la aplicación. `reservations.service` consume la interfaz pública `tables.service.TableCatalog`, que devuelve IDs de mesas. La entidad `DiningTable` y su repositorio pertenecen a `tables`. La capa `tables.controller` queda reservada para futuros casos HTTP. El módulo `notifications` conserva su lugar como módulo independiente del monolito para una feature posterior.

### Cambios previstos

1. Eliminar el cálculo de duración en `CreateReservationService`. Mantener el bloqueo ordenado de las mesas candidatas bajo `READ COMMITTED` y consultar las asignaciones en una sentencia posterior al bloqueo.
2. Actualizar `ReservationRepository.findOccupiedTableIds` para consultar las candidatas confirmadas por igualdad en `reservation_time` y `reservation_time_remainder`. Reutilizar la representación de `ReservationStart`, el índice parcial y las migraciones aplicadas.
3. Actualizar los casos de `CreateReservationHttpTest` que dependen de la regla anterior y probar las reservas históricas. Ejecutar `ReservationTransactionHttpTest` para verificar espera, visibilidad después del commit y rollback. Validar el [contrato](contracts/openapi.yaml) con la [guía](quickstart.md) y ejecutar `./mvnw verify`.

Si varias instancias comparten la base, coordinar el cambio de las que atienden el POST para aplicar la misma regla de disponibilidad. La actualización reutiliza el esquema de las instalaciones existentes.

### Cobertura de aceptación

| Requisitos | Comprobación prevista |
| --- | --- |
| FR-001 a FR-004, FR-011 | POST válido sin credenciales, código único y estado final consultable. Entrada inválida, incluida cantidad fraccionaria, devuelve 400 sin cambios. |
| FR-005 a FR-009, SC-003 | Capacidad exacta, insuficiente y catálogo vacío. Otra mesa libre cuando la primera está asignada. Instante igual, instantes distintos en una misma fecha o en fechas distintas, desplazamientos equivalentes que cruzan medianoche y precisión submicrosegundo. Dos POST simultáneos para una única mesa y el mismo instante producen una confirmación y un rechazo. Verificar el rollback y la relación entre mesa y estado. |
| FR-010 y FR-012 | GET repetido para ambos resultados, código desconocido y formato e idioma de errores. Verificar que la consulta conserva la decisión original. |
| FR-013, historia 1 escenario 9, SC-005 | Enviar 13 con una mesa libre de capacidad suficiente y verificar 400 en español, sin código, fila nueva ni llamada al servicio de creación. Verificar que 12 sigue obteniendo 201 con el resultado que corresponda a la disponibilidad. Consultar por código una reserva histórica superior a 12. |
| FR-014, historia 2 escenario 5, SC-006 | Preparar reservas existentes, incluidas filas con el resto decimal vacío por defecto. Consultar los códigos originales y verificar que la confirmada bloquea el instante exacto y permite reutilizar la mesa para un instante distinto. Comparar fecha y hora, código, estado y mesa antes y después. |

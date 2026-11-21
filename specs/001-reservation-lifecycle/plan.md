# Implementation Plan — Ciclo de vida de una reserva

**Rama Git** `master` · **Feature Spec Kit** `001-reservation-lifecycle` · **Fecha** 2026-11-21 · [Especificación](spec.md)

## Summary

El POST admite cantidades enteras de 1 a 12 comensales. Las cantidades superiores a 12 reciben un error de validación en español antes de crear una reserva o consultar disponibilidad (FR-013). Las solicitudes válidas siguen el flujo transaccional y reciben un código con la decisión definitiva. El cliente puede recuperar el estado con su código, también para reservas anteriores al nuevo límite.

## Technical Context

| Aspecto | Elección |
| --- | --- |
| Aplicación | Un JAR ejecutable en JVM, Java 25, Maven Wrapper 3.9.x, Spring Boot 4.2.0 |
| Dependencias | Spring MVC, Data JPA, Bean Validation, MapStruct 1.6.3, driver JDBC PostgreSQL y Flyway con soporte PostgreSQL |
| Datos | PostgreSQL 17, catálogo de mesas preexistente y reservas persistentes |
| Pruebas | JUnit Jupiter, pruebas HTTP con Spring Boot y transacciones independientes contra PostgreSQL mediante Testcontainers o una base de pruebas configurada |
| Plataforma y alcance | Servicio HTTP para un restaurante. Las dos operaciones públicas funcionan sin cuenta. La decisión síncrona y la exclusión de solapes son requisitos funcionales. |
| Base del proyecto | El backend, las migraciones y las pruebas HTTP ya existen. El cambio usa el DTO, la validación MVC y el manejo de errores actuales. |

## Constitution Check

La revisión de entrada y la revisión posterior a la fase 1 cumplen los seis principios de la [constitución 1.3.0](../../.specify/memory/constitution.md).

| Principio | Verificación de entrada | Evidencia en el diseño |
| --- | --- | --- |
| I. Capas | Usar exactamente `controller`, `service`, `repository` y `model` dentro de cada módulo | `controller` contiene adaptadores HTTP, DTOs y mappers MapStruct. `service` decide y orquesta. `repository` contiene interfaces Spring Data. `model` contiene entidades JPA y enums. |
| II. Monolito modular | Un artefacto y límites por dominio | `reservations` solicita los IDs candidatos por la interfaz pública de `tables`. Cada módulo accede a su propio repositorio. Notificaciones queda fuera de la feature. |
| III. HTTP y mapeo | Dos rutas versionadas con DTOs | [Contrato](contracts/openapi.yaml) con DTOs de entrada, creación y consulta. MapStruct proyecta entidades a respuestas que contienen solo campos públicos. |
| IV. Validación y errores | Validación antes del servicio y tratamiento global | El DTO incorpora `@Max(12)`. El controlador valida mediante `@Valid` antes de iniciar la creación. `@RestControllerAdvice` devuelve ProblemDetail conforme a RFC 7807, incluido el 400 por exceder el máximo. |
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

1. Añadir `@Max(12)` a `CreateReservationRequest.partySize` y usar el detalle en español definido en el [contrato](contracts/openapi.yaml). Conservar la validación de obligatoriedad y positividad y la lectura de valores enteros mediante el deserializador actual.
2. Para valores válidos, conservar la decisión transaccional descrita en el [modelo](data-model.md). El máximo se aplica al DTO de creación. El esquema existente permite consultar reservas anteriores y conservar su ocupación.
3. Actualizar los casos de `CreateReservationHttpTest` que esperan persistencia para cantidades superiores a 12. Verificar el tamaño máximo con y sin una mesa apta. En una prueba MVC enfocada, enviar 13 y comprobar que el servicio de creación no recibe llamadas. Ejecutar la [guía](quickstart.md) y la suite de regresión.

### Cobertura de aceptación

| Requisitos | Comprobación prevista |
| --- | --- |
| FR-001 a FR-004, FR-011 | POST válido sin credenciales, código único y estado final consultable. Entrada inválida, incluida cantidad fraccionaria, devuelve 400 sin cambios. |
| FR-005 a FR-009 | Capacidad exacta, insuficiente y catálogo vacío. Una mesa ocupada y otra apta libre. Solape parcial, adyacencia, medianoche y solicitudes simultáneas. Verificar en persistencia la relación entre mesa y estado. |
| FR-010 y FR-012 | GET repetido para ambos resultados, código desconocido y formato e idioma de errores. Verificar que la consulta conserva la decisión original. |
| FR-013, historia 1 escenario 9, SC-005 | Enviar 13 con una mesa libre de capacidad suficiente y verificar 400 en español, sin código, fila nueva ni llamada al servicio de creación. Verificar que 12 sigue obteniendo 201 con el resultado que corresponda a la disponibilidad. Consultar por código una reserva histórica superior a 12. |

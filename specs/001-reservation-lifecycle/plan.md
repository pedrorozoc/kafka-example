# Implementation Plan — Ciclo de vida de una reserva

**Rama Git** `master` · **Feature Spec Kit** `001-reservation-lifecycle` · **Fecha** 2026-11-20 · [Especificación](spec.md)

## Summary

El cliente envía una solicitud válida y obtiene el código de su reserva con la decisión definitiva en la misma respuesta. El servicio decide y persiste el resultado dentro de una sola transacción. El cliente puede recuperar el estado con su código.

## Technical Context

| Aspecto | Elección |
| --- | --- |
| Aplicación | Un JAR ejecutable en JVM, Java 25, Maven Wrapper 3.9.x, Spring Boot 4.2.0 |
| Dependencias | Spring MVC, Data JPA, Bean Validation, MapStruct 1.6.3, driver JDBC PostgreSQL y Flyway con soporte PostgreSQL |
| Datos | PostgreSQL 17, catálogo de mesas preexistente y reservas persistentes |
| Pruebas | JUnit Jupiter, pruebas HTTP con Spring Boot y transacciones independientes contra PostgreSQL mediante Testcontainers |
| Plataforma y alcance | Servicio HTTP para un restaurante. Las dos operaciones públicas funcionan sin cuenta. La decisión síncrona y la exclusión de solapes son requisitos funcionales. |
| Base del proyecto | El repositorio contiene documentación de Spec Kit. El plan incluye el arranque mínimo del backend, las migraciones y las instrucciones para reproducirlo en README.md. |

## Constitution Check

La revisión de entrada aplicó la versión 1.2.0 de la [constitución](../../.specify/memory/constitution.md). El borrador anterior separaba DTOs y mappers en paquetes propios. Se corrigió la estructura antes de continuar con el diseño. La revisión posterior a la fase 1 da por cumplidos los cinco principios en el plan.

| Principio | Verificación de entrada | Evidencia en el diseño |
| --- | --- | --- |
| I. Capas | Usar exactamente `controller`, `service`, `repository` y `model` dentro de cada módulo | `controller` contiene adaptadores HTTP, DTOs y mappers MapStruct. `service` decide y orquesta. `repository` contiene interfaces Spring Data. `model` contiene entidades JPA y enums. |
| II. Monolito modular | Un artefacto y límites por dominio | `reservations` solicita los IDs candidatos por la interfaz pública de `tables`. Cada módulo accede a su propio repositorio. Notificaciones queda fuera de la feature. |
| III. HTTP y mapeo | Dos rutas versionadas con DTOs | [Contrato](contracts/openapi.yaml) con DTOs de entrada, creación y consulta. MapStruct proyecta entidades a respuestas que contienen solo campos públicos. |
| IV. Validación y errores | Validación antes del servicio y tratamiento global | DTO con Bean Validation y controlador con `@Valid`. `@RestControllerAdvice` emite ProblemDetail conforme a RFC 7807 para validación, lectura de JSON, código desconocido y fallos del sistema. |
| V. Idioma | Identificadores en inglés, textos en español | Campos y estados en inglés en el contrato. Títulos, detalles, mensajes de excepción, logs y comentarios en español. |

La revisión de implementación comprobará el README de arranque y la convención de commits `clase-NN-slug`.

## Project Structure

Los artefactos de la feature son [especificación](spec.md), [decisiones y alternativas](research.md), [modelo](data-model.md), [contrato HTTP](contracts/openapi.yaml) y [guía de validación](quickstart.md).

Estructura prevista desde la raíz del repositorio

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

### Flujo de implementación

1. Preparar build, migraciones, catálogo mínimo de mesas y arranque documentado. La fixture de cuatro plazas pertenece a validación local.
2. Definir el modelo y sus transiciones. En el POST, validar los datos y crear en memoria una reserva `PENDING` con código propio.
3. En la transacción del servicio, pedir a `tables` que bloquee las candidatas. Leer las ocupaciones confirmadas tras adquirir los bloqueos, elegir una mesa libre o rechazar y persistir solo el resultado final. El [modelo](data-model.md) especifica el orden de las consultas y la condición de solape.
4. Exponer creación y consulta conforme al contrato. Responder al POST una vez completado el commit. En GET buscar por código y mapear el estado guardado.
5. Verificar los resultados públicos y las invariantes de persistencia siguiendo la [guía](quickstart.md). Para el caso simultáneo, usar peticiones y conexiones diferentes. Para errores de persistencia, verificar rollback sin reserva visible.

### Cobertura de aceptación

| Requisitos | Comprobación prevista |
| --- | --- |
| FR-001 a FR-004, FR-011 | POST válido sin credenciales, código único y estado final consultable. Entrada inválida, incluida cantidad fraccionaria, devuelve 400 sin cambios. |
| FR-005 a FR-009 | Capacidad exacta, insuficiente y catálogo vacío. Una mesa ocupada y otra apta libre. Solape parcial, adyacencia, medianoche y solicitudes simultáneas. Verificar en persistencia la relación entre mesa y estado. |
| FR-010 y FR-012 | GET repetido para ambos resultados, código desconocido y formato e idioma de errores. Verificar que la consulta conserva la decisión original. |

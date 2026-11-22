# Implementation Plan — Ciclo de vida de una reserva

**Rama Git** `master` · **Feature Spec Kit** `001-reservation-lifecycle` · **Fecha** 2026-11-22 · [Especificación](spec.md)

## Summary

La llegada se representa con un solo `LocalDateTime` a precisión de minuto. El POST recibe una fecha y hora local como `2030-06-12T19:00` y un `partySize` de tipo `Integer`, entre 1 y 12. Se cambia la persistencia a `timestamp without time zone` y `integer`. El bloqueo transaccional conserva la exclusividad de nuevas confirmaciones por mesa, fecha y minuto. Una migración convierte las reservas existentes al minuto local y conserva sus códigos, estados y mesas.

## Technical Context

| Aspecto | Elección |
| --- | --- |
| Aplicación | Un JAR ejecutable en JVM, Java 25, Maven Wrapper 3.9.x, Spring Boot 4.2.0 |
| Dependencias | Spring MVC, Data JPA, Bean Validation, MapStruct 1.6.3, driver JDBC PostgreSQL y Flyway con soporte PostgreSQL |
| Datos | PostgreSQL 17, catálogo de mesas preexistente y reservas persistentes |
| Pruebas | JUnit Jupiter, pruebas HTTP con Spring Boot y transacciones independientes contra PostgreSQL mediante Testcontainers o una base de pruebas configurada |
| Plataforma y alcance | Servicio HTTP para un restaurante. Las dos operaciones públicas funcionan sin cuenta. Cada POST válido devuelve la decisión definitiva en la misma solicitud. |
| Base del proyecto | V1 a V3 usan `timestamptz`, una columna de fracción adicional y `numeric`. V4 cambia las columnas con una migración transaccional. |
| Zona histórica | `America/Bogota` como valor de trabajo. La instalación puede indicar su zona IANA mediante `BISTRO_LEGACY_RESERVATION_ZONE` antes de aplicar V4. El POST opera con hora local. |
| Restricciones de integridad | Cada minuto histórico confirmado ocupa la mesa. Las coincidencias históricas se conservan. Se bloquean las nuevas confirmaciones en un minuto ocupado. |
| Objetivos de rendimiento | La especificación acota el trabajo a la corrección funcional de la disponibilidad y la concurrencia. |

## Constitution Check

La revisión de entrada y la revisión posterior a la fase 1 cumplen los seis principios de la [constitución 1.3.0](../../.specify/memory/constitution.md).

| Principio | Verificación de entrada | Evidencia en el diseño |
| --- | --- | --- |
| I. Capas | Usar exactamente `controller`, `service`, `repository` y `model` dentro de cada módulo | `controller` contiene adaptadores HTTP, DTOs y mappers MapStruct. `service` decide y orquesta. `repository` contiene interfaces Spring Data. `model` contiene entidades JPA y enums. |
| II. Monolito modular | Un artefacto y límites por dominio | `reservations` solicita los IDs candidatos por la interfaz pública de `tables`. Cada módulo accede a su propio repositorio. Notificaciones queda fuera de la feature. |
| III. HTTP y mapeo | Dos rutas versionadas con DTOs | [Contrato](contracts/openapi.yaml) con DTOs de entrada, creación y consulta. MapStruct proyecta entidades a respuestas que contienen solo campos públicos. |
| IV. Validación y errores | Validación antes del servicio y tratamiento global | El controlador conserva `@Valid` y las restricciones de cantidad. Los lectores de entrada validan el formato local y el valor numérico antes de consultar disponibilidad. El advice global devuelve `ProblemDetail` en español. |
| V. Idioma | Identificadores en inglés, textos en español | Campos y estados en inglés en el contrato. Títulos, detalles, mensajes de excepción, logs y comentarios en español. |
| VI. Dependencias entre módulos | Encapsular dependencias volátiles detrás de una interfaz del proveedor | `tables.service.TableCatalog` es la interfaz pública. `DatabaseTableCatalog` tiene visibilidad de paquete y encapsula el mecanismo de persistencia y bloqueo. |

El diseño cumple la constitución tras revisar los contratos de éxito y error, el límite entre módulos y la validación previa al servicio. La implementación actualizará el README de arranque con la configuración de la zona y la transición de versiones.

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

1. Añadir V4 sin editar V1 a V3. Validar la zona histórica y el rango representable de las cantidades existentes. Convertir cada instante a hora local y truncar al minuto dentro de la misma transacción, retirar la columna de fracción adicional y conservar el índice parcial por mesa y fecha de llegada. Cambiar `party_size` a `integer` con una restricción de valor positivo.
2. Adaptar el DTO, la entidad, el servicio y la consulta al minuto local, y retirar `ReservationStart` y su conversión. Validar la forma exacta y la fecha de calendario antes de llamar al servicio. Representar `partySize` como `Integer` en el DTO y la entidad, y transferir el valor validado a `TableCatalog` y su repositorio como `int`. Mantener la aceptación de números JSON cuyo valor sea entero y la respuesta 400 en español para valores inválidos.
3. Conservar el orden de bloqueo por ID de las mesas candidatas y la consulta posterior bajo `READ COMMITTED`. Buscar confirmaciones por igualdad de fecha y minuto. Conservar el índice no único para los casos históricos previstos en FR-014.
4. Adaptar las pruebas HTTP y transaccionales. Añadir una prueba de migración desde V3 que cubra datos históricos y fallos de conversión, y conservar una prueba de arranque desde una base vacía. Aplicar la [guía de validación](quickstart.md) y ejecutar `./mvnw verify`.

V4 cambia el esquema que valida el servicio al arrancar. Coordinar una actualización con todas las instancias anteriores detenidas, conservar un respaldo previo y verificar la zona del restaurante antes de abrir tráfico con la nueva versión. Los clientes que enviaban segundos o desplazamientos deben adoptar el nuevo formato del POST. El GET por código conserva su contrato.

### Cobertura de aceptación

| Requisitos | Comprobación prevista |
| --- | --- |
| FR-001 a FR-004, FR-011 | POST válido sin credenciales, HTTP 201, código único, `Location` y estado definitivo consultable. Fecha imposible, segundos, fracción y desplazamiento devuelven 400. Verificar la hora persistida a precisión de minuto. |
| FR-005 a FR-009, SC-003 | Capacidad exacta, insuficiente, catálogo vacío y mesa alternativa. Igual fecha y minuto producen conflicto. El minuto siguiente y el mismo minuto de otro día permiten reutilizar la mesa. Dos POST simultáneos para la única mesa producen una confirmación y un rechazo. Un rollback libera la mesa para otra solicitud. |
| FR-010 y FR-012 | GET repetido para ambos resultados, código desconocido y formato e idioma de los errores. |
| FR-013, SC-005 | Probar 1 y 12 válidos y 0, 13, fracciones, texto y desbordamientos inválidos. Verificar que las cantidades inválidas producen 400 sin llamada al servicio ni cambios de datos. Cubrir formas numéricas equivalentes como `12.0` y `1.2e1`. |
| FR-014, SC-006 | Migrar datos V3 con llegada cerca de la medianoche UTC, dos confirmaciones históricas para la misma mesa y minuto local y una cantidad histórica de 13. Consultar los códigos originales, comparar estado y asignación y comprobar que el minuto queda ocupado. Verificar rechazo atómico ante datos que la conversión no puede representar. |

<!--
Informe de impacto
Versión sin ratificar → 1.0.0 (adopción inicial).
Principios incorporados: diseño por capas, monolito modular, contratos HTTP,
validación y errores, e idioma del proyecto.
Secciones incorporadas: convenciones del repositorio, revisión de cambios y gobernanza.
Secciones eliminadas: ninguna. Pendientes: ninguno.
-->

# Bistro Constitution

## Core Principles

### I. Calidad del código y separación por capas


El código debe aplicar SOLID y prácticas de código limpio. La separación de responsabilidades debe seguir un patrón de capas por módulo. 

**Estructura de paquetes DENTRO de cada módulo: exactamente** `controller`, `service`, `repository` y `model`. Cada uno con una única responsabilidad:
- `controller`: adaptadores de entrada HTTP (controllers), DTOs de request/response y mappers MapStruct.
- `service`: lógica de negocio y orquestación.
- `repository`: interfaces de acceso a datos (Spring Data)
- `model`: entidades JPA y enums de dominio.

NO usar capas o paquetes `domain`, `application`, `api`, `infrastructure` ni capas de arquitectura hexagonal.   

El manejo global de excepciones debe implementarse con @RestControllerAdvice y ProblemDetail (RFC 7807). La validación de entrada debe realizarse con Bean Validation en los DTOs.


### II. Monolito modular

Bistro debe desplegarse como un único artefacto. Los módulos `reservations`, `tables` y
`notifications` deben ocupar subpaquetes propios. Cada módulo debe ser dueño de su dominio,
de su repositorio y de las interfaces públicas que ofrece a otros módulos. Toda interacción
entre módulos debe pasar por esas interfaces. Así, un módulo puede cambiar su persistencia
sin obligar a sus consumidores a modificar sus consultas.

### III. Contratos HTTP y mapeo

Todos los endpoints deben publicarse bajo `/api/v1/`. Los cuerpos de solicitud y respuesta
de negocio deben definirse mediante DTOs. Está prohibido exponer entidades JPA en la API,
incluso como campos de un DTO. El mapeo entre entidades y DTOs debe realizarse con MapStruct.

### IV. Validación de entrada y manejo de errores

Los DTOs de entrada deben declarar sus restricciones con Bean Validation. El controlador
debe activar la validación antes de delegar la solicitud al servicio. Las excepciones de
la API, incluidos los errores de validación, deben tratarse de forma global con
`@RestControllerAdvice`. Las respuestas de error deben usar `ProblemDetail` conforme
a RFC 7807 para ofrecer el mismo formato a los clientes de todos los módulos.

### V. Idioma del proyecto

Los identificadores del código, incluidos los nombres de clases y métodos, deben escribirse
en inglés. Los comentarios, los mensajes de log, los mensajes de excepción y los textos
dirigidos al cliente deben escribirse en español. Esta regla incluye el título y el detalle
de los errores que devuelve la API.

## Convenciones del repositorio

Cada mensaje de commit debe seguir el formato `clase-NN-slug`, donde `NN` representa
el número de clase con dos dígitos y `slug` describe el cambio. El archivo `README.md`
debe indicar los requisitos y los pasos para arrancar el proyecto desde una copia del
repositorio. Todo cambio en el procedimiento de arranque debe actualizar esas instrucciones.

## Revisión de cambios

Al revisar un cambio se debe comprobar el cumplimiento de los principios que afecta.
Por ejemplo, si una reserva consulta la disponibilidad de una mesa, la revisión debe
verificar que `reservations` utiliza la interfaz pública de `tables`. Si cambia un endpoint,
se deben revisar tanto su contrato de éxito como su contrato de error.

## Governance

Esta constitución rige las decisiones de diseño y las convenciones del repositorio.
Para enmendarla, la propuesta debe identificar la regla afectada, justificar el cambio y
describir su impacto en el código existente. La persona responsable del proyecto debe
aprobar la enmienda antes de integrarla y la revisión debe verificar la versión y la fecha.

La versión sigue el esquema `MAJOR.MINOR.PATCH`. Se incrementa `MAJOR` al eliminar un
principio o redefinirlo de forma incompatible, `MINOR` al añadir un principio o ampliar
sustancialmente su alcance y `PATCH` al aclarar la redacción sin cambiar las obligaciones.

**Version**: 1.2.0 | **Ratified**: 2026-11-20 | **Last Amended**: 2026-11-20

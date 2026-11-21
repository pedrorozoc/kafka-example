# Feature Specification: Ciclo de vida de una reserva en bistro

**Feature Branch**: `master` (rama actual; sin hook de creación de rama)

**Created**: 2026-11-20

**Status**: Ready for planning — validada, sin aclaraciones pendientes

**Input**: Un cliente solicita una mesa para un único restaurante mediante HTTP, enviando nombre, email, fecha y hora y cantidad de comensales. En la misma solicitud, bistro asigna una mesa y confirma la reserva o la rechaza por falta de disponibilidad. El cliente consulta el resultado por código. Backend sin interfaz gráfica ni autenticación.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Solicitar una reserva y conocer su resultado definitivo (Priority: P1)

Como cliente, quiero enviar mis datos y los de mi visita para saber, en la respuesta a mi solicitud, si tengo una mesa confirmada o si mi reserva fue rechazada.

**Why this priority**: Es el propósito principal: decidir si el restaurante puede recibir al grupo y comunicar una decisión definitiva.

**Independent Test**: Preparar mesas existentes con capacidades y ocupaciones conocidas y enviar solicitudes por HTTP. Verificar código, estado y asignación resultante, sin cuentas ni interfaz gráfica.

**Acceptance Scenarios**:

1. **Given** una mesa libre en la franja solicitada con capacidad mayor o igual a la cantidad de comensales, **When** el cliente envía los datos obligatorios válidos, **Then** recibe una respuesta exitosa con un código y estado `CONFIRMED`, y la reserva tiene esa mesa asignada.
2. **Given** que no hay mesa libre con capacidad suficiente en esa franja, **When** se envía una solicitud válida, **Then** se recibe una respuesta exitosa con un código y estado `REJECTED`, sin mesa asignada; no se presenta como error del sistema.
3. **Given** una mesa ocupada en esa franja y otra libre con capacidad suficiente, **When** se solicita la reserva, **Then** se confirma con una mesa libre y no se reutiliza la ocupada.
4. **Given** mesas libres cuya capacidad individual es menor que la cantidad solicitada, **When** se solicita la reserva, **Then** se devuelve `REJECTED`, aunque la suma de capacidades alcance para el grupo.
5. **Given** una reserva confirmada que ocupa la única mesa apta para esa franja, **When** otro cliente solicita la misma franja, **Then** la segunda reserva queda `REJECTED` y la primera conserva su asignación.
6. **Given** una solicitud sin algún dato obligatorio, con nombre vacío, email de formato inválido, fecha y hora inválidas o cantidad no entera o menor que uno, **When** se envía, **Then** se informa un error de validación en español, sin crear reserva ni ocupar mesa.

7. **Given** que la única mesa apta está asignada de 19:00 a 20:30, **When** se solicita para las 20:00 de la misma fecha, **Then** se devuelve `REJECTED` por superposición, aunque las horas de inicio sean distintas.
8. **Given** que la única mesa apta está asignada de 19:00 a 20:30, **When** se solicita para las 20:30 de la misma fecha, **Then** se devuelve `CONFIRMED` con esa mesa porque los intervalos son adyacentes.
9. **Given** una solicitud cuya cantidad de comensales supera las 12 personas, **When** el cliente la envía, **Then** el sisema la rechaza por validación de entrada y no crea la reserva ni evalúa disponibilidad de mesas.


---

### User Story 2 - Consultar una reserva por su código (Priority: P2)

Como cliente, quiero consultar el estado de mi reserva usando el código recibido, sin crear una cuenta.

**Why this priority**: Permite recuperar la decisión tomada después de la solicitud original.

**Independent Test**: Con reservas previamente creadas en cada estado final, consultar sus códigos por HTTP y comparar los estados. También consultar un código inexistente.

**Acceptance Scenarios**:

1. **Given** un código de una reserva `CONFIRMED`, **When** se consulta, **Then** se recibe exitosamente el estado `CONFIRMED` de esa reserva.
2. **Given** un código de una reserva `REJECTED`, **When** se consulta, **Then** se recibe exitosamente `REJECTED`, incluso si posteriormente hay disponibilidad.
3. **Given** un código que no corresponde a ninguna reserva, **When** se consulta, **Then** se recibe un error de reserva no encontrada en español, sin crear ni modificar reservas.
4. **Given** una reserva con resultado definitivo, **When** se consulta repetidas veces, **Then** se obtiene el mismo estado; consultar no reevalúa disponibilidad ni cambia la asignación.

### Edge Cases

- Capacidad exacta: una mesa libre con capacidad igual a `partySize` es apta.
- Si `partySize` está entre 1 y 12 y el resto de la solicitud es válido, un catálogo vacío, la falta de mesas libres o un grupo mayor que la capacidad de cualquier mesa producen `REJECTED`.
- Varias mesas aptas: se asigna exactamente una; no se exige una política de preferencia.
- Mesa ocupada en otra franja: esa ocupación no impide asignarla a una franja sin conflicto, según los intervalos de 90 minutos definidos en FR-005.
- Cruce de medianoche: una reserva a las 23:30 ocupa hasta la 01:00 del día siguiente; una solicitud a las 00:30 de ese día entra en conflicto y una a la 01:00 no.
- Solicitudes simultáneas por una mesa: como máximo una puede confirmarse con esa mesa para la misma franja. Es una regla de integridad, sin objetivos de volumen ni rendimiento.
- Reserva rechazada: no ocupa mesa ni impide confirmar solicitudes posteriores.
- Datos inválidos y códigos inexistentes son errores; la falta de mesa para una solicitud válida es un resultado de negocio exitoso.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir solicitar y consultar reservas sin autenticación ni cuenta. Nombre y email son datos de contacto, no credenciales.
- **FR-002**: La creación DEBE recibir `customerName`, `customerEmail`, `reservationTime` (fecha y hora) y `partySize`. Todos son obligatorios. El nombre debe contener texto no vacío, el email debe tener formato válido, la fecha y hora deben ser interpretables y la cantidad debe ser un entero mayor o igual a uno. Una entrada inválida DEBE rechazarse sin crear reserva ni asignar mesa.
- **FR-003**: Cada solicitud válida DEBE generar una reserva con un `reservationCode` público, único y estable, conservando los datos del cliente y de la visita para resolverla y consultarla después.
- **FR-004**: La reserva DEBE nacer en `PENDING` y resolverse en el mismo flujo de creación, antes de responder exitosamente. La respuesta DEBE contener código y resultado definitivo `CONFIRMED` o `REJECTED`; `PENDING` es transitorio y no es el resultado de una creación exitosa.
- **FR-005**: Una mesa DEBE considerarse apta si su capacidad es mayor o igual a `partySize` y no está asignada a otra reserva en la franja solicitada. Cada reserva ocupa 90 minutos desde `reservationTime`, incluyendo el instante inicial y excluyendo el instante final. Dos reservas compiten por una mesa cuando sus intervalos se superponen; si una comienza exactamente al terminar la otra, no hay conflicto.
- **FR-006**: Si existe una mesa apta, el sistema DEBE asignar exactamente una y terminar en `CONFIRMED`. Ninguna reserva puede confirmarse sin `assignedTableId`. No se combinan mesas para una reserva.
- **FR-007**: Si no existe mesa apta, el sistema DEBE terminar en `REJECTED`, sin `assignedTableId`, y comunicarlo como respuesta exitosa de negocio.
- **FR-008**: Una mesa NO DEBE quedar asignada a más de una reserva en la misma franja, incluso ante solicitudes simultáneas. Una reserva rechazada no ocupa mesas.
- **FR-009**: Los únicos estados DEBEN ser `PENDING`, `CONFIRMED` y `REJECTED`. Las únicas transiciones DEBEN ser `PENDING` → `CONFIRMED` y `PENDING` → `REJECTED`. Los estados finales no cambian ni se reevalúan mediante consulta.
- **FR-010**: El cliente DEBE poder obtener el estado mediante el código, sin email ni datos adicionales. Un código inexistente DEBE producir un error de reserva no encontrada.
- **FR-011**: La feature DEBE exponer los dos casos de uso HTTP definidos abajo, bajo `/api/v1/`, y ser verificable por HTTP sin interfaz gráfica.
- **FR-012**: Los mensajes al cliente DEBEN estar en español. Los errores de validación y reserva no encontrada DEBEN seguir el formato uniforme de errores del proyecto; `REJECTED` pertenece a la respuesta exitosa.
- **FR-013**: El sistema DEBE rechazar por validación de entrada toda solicitud cuya cantidad de comensales supere las 12 personas, sin crear la reserva ni evaluar disponibilidad de mesas. Este límite es una validación de entrada (solicitud inválida), distinta del rechazo de negocio (`REJECTED`): El `REJECTED` aplica a solicitudes válidas para las que no hay mesa disponible.

### Contrato HTTP solicitado

Estos endpoints forman parte del alcance explícito del usuario. No se fijan lenguajes, librerías, almacenamiento ni estructura del código.

| Operación | Entrada | Resultado exitoso | Error de entrada o consulta |
| --- | --- | --- | --- |
| `POST /api/v1/reservations` | `customerName`, `customerEmail`, `reservationTime`, `partySize` | Código de reserva y estado final `CONFIRMED` o `REJECTED`; ambos son resultados exitosos | Datos obligatorios ausentes o inválidos |
| `GET /api/v1/reservations/{reservationCode}` | Código en la ruta | Estado actual de la reserva identificada | Reserva no encontrada |

### Key Entities *(include if feature involves data)*

- **Reservation**: Solicitud de un grupo para una visita al único restaurante. Contiene `reservationCode` (identificador público), `customerName`, `customerEmail`, `reservationTime`, `partySize`, `status` y `assignedTableId`. La mesa está presente en una reserva confirmada y ausente en una pendiente o rechazada.
- **Mesa existente**: Recurso identificado por un id y una capacidad conocida, usado para evaluar disponibilidad y asignar reservas. Crear o editar mesas pertenece a otra feature.
- **Franja horaria**: Intervalo de 90 minutos que comienza en `reservationTime`, incluye el inicio y excluye el final. Los intervalos superpuestos compiten por una mesa; no se incorpora un caso de uso para gestionar franjas.

## Success Criteria *(mandatory)*

### Measurable Outcomes

Se definen resultados funcionales verificables con los escenarios de aceptación, sin métricas de negocio, satisfacción, latencia, volumen ni concurrencia.

- **SC-001**: En los casos con mesa apta, el cliente obtiene código y confirmación definitiva en la misma solicitud; cada reserva confirmada tiene exactamente una mesa de capacidad suficiente.
- **SC-002**: En los casos sin mesa apta, el cliente obtiene código y rechazo definitivo como resultado exitoso, sin mesa asignada.
- **SC-003**: Ninguna pareja de reservas confirmadas de los escenarios de aceptación comparte mesa en la misma franja.
- **SC-004**: Cada código creado en los escenarios devuelve en consultas posteriores la misma decisión final recibida al solicitar la reserva; un código inexistente devuelve reserva no encontrada.
- **SC-005**: En cada caso de entrada inválida de la historia 1 se comunica el error sin crear reservas ni ocupar mesas.

## Assumptions

- **Dependencia de mesas**: El catálogo y sus capacidades están disponibles como datos preexistentes. Esta feature los consume sin añadir gestión de mesas.
- **Selección de mesa**: Cualquier mesa libre con capacidad suficiente satisface la solicitud. No se exige elegir la de menor capacidad ni combinar mesas.
- **Validación mínima** — Se aplican las reglas de FR-002 y el máximo de 12 comensales de FR-013. Se aceptan solicitudes sin restricciones de anticipación ni de horario de apertura.
- **Identificación**: El código basta para consultar el estado. El email no se verifica mediante mensajes ni representa una cuenta.
- **Franja acordada**: El cliente definió intervalos de duración fija de 90 minutos. Se adopta como convención que dos intervalos adyacentes no se superponen, sin tiempo adicional entre reservas.

### Límites del alcance

- Backend de reservas de un único restaurante, sin interfaz gráfica ni múltiples locales.
- Sin autenticación ni cuentas de usuario.
- Sin pagos, menú ni pedidos de comida.
- Sin cancelaciones, reprogramaciones ni lista de espera.
- Sin creación ni edición de mesas.
- Sin envío de notificaciones.
- Sin otros estados, transiciones o decisiones posteriores a la respuesta de creación.
- Sin métricas de negocio, satisfacción, tiempos ni objetivos de concurrencia.

# Feature Specification: Ciclo de vida de una reserva en bistro

**Feature Branch**: `master` (rama actual; sin hook de creación de rama)

**Created**: 2026-11-20

**Status**: Ready for planning — validada, sin aclaraciones pendientes

**Input**: Un cliente solicita una mesa para un único restaurante mediante HTTP, enviando nombre, email, una cantidad de 1 a 12 comensales y una sola fecha y hora local de llegada con precisión de minuto, sin especificar duración. En la misma solicitud, bistro asigna una mesa y confirma la reserva o la rechaza por falta de disponibilidad. El cliente consulta el resultado por código. Backend sin interfaz gráfica ni autenticación.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Solicitar una reserva y conocer su resultado definitivo (Priority: P1)

Como cliente, quiero enviar mis datos y los de mi visita para saber, en la respuesta a mi solicitud, si tengo una mesa confirmada o si mi reserva fue rechazada.

**Why this priority**: Es el propósito principal: decidir si el restaurante puede recibir al grupo y comunicar una decisión definitiva.

**Independent Test**: Preparar mesas existentes con capacidades y ocupaciones conocidas y enviar solicitudes por HTTP. Verificar código, estado y asignación resultante, sin cuentas ni interfaz gráfica.

**Acceptance Scenarios**:

1. **Given** una mesa disponible con capacidad mayor o igual a la cantidad de comensales, **When** el cliente envía los datos obligatorios válidos con una sola fecha y hora de llegada, **Then** recibe un código y el estado `CONFIRMED`, con esa mesa asignada y sin duración ni hora de finalización en la reserva.
2. **Given** que no hay mesa disponible con capacidad suficiente en el minuto solicitado, **When** se envía una solicitud válida, **Then** se recibe un código y el estado `REJECTED` como resultado exitoso de negocio, sin mesa asignada.
3. **Given** una mesa asignada en el minuto solicitado y otra disponible con capacidad suficiente, **When** se solicita la reserva, **Then** se confirma con la mesa disponible.
4. **Given** mesas libres cuya capacidad individual es menor que la cantidad solicitada, **When** se solicita la reserva, **Then** se devuelve `REJECTED`, aunque la suma de capacidades alcance para el grupo.
5. **Given** una reserva confirmada que tiene asignada la única mesa con capacidad suficiente, **When** otro cliente solicita una mesa para la misma fecha y minuto, **Then** la segunda reserva queda `REJECTED` y la primera conserva su asignación.
6. **Given** una solicitud sin algún dato obligatorio, con nombre vacío, email de formato inválido, fecha y hora inválidas o cantidad no entera o menor que uno, **When** se envía, **Then** se informa un error de validación en español, sin crear reserva ni ocupar mesa.
7. **Given** que la única mesa con capacidad suficiente está asignada a las 19:00, **When** otro cliente solicita una mesa a las 19:01 de la misma fecha o a las 19:00 del día siguiente, en pruebas independientes, **Then** se devuelve `CONFIRMED` con esa misma mesa.
8. **Given** solicitudes que incluyen segundos, fracciones de segundo o un desplazamiento horario en `reservationTime`, **When** se envía cada variante por separado, **Then** se informa un error de validación en español y se omite la creación de la reserva y la evaluación de disponibilidad.
9. **Given** una mesa libre con capacidad para 12 personas, **When** el cliente solicita una reserva para 1 o 12 comensales en pruebas independientes, **Then** ambas solicitudes se confirman. Si la cantidad enviada es 0 o 13, se devuelve un error de validación antes de crear la reserva o evaluar la disponibilidad.

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
5. **Given** una reserva histórica confirmada cuya hora local es 19:00:45, **When** se consulta por su código original y se solicita la misma mesa a las 19:00 y a las 19:01 de esa fecha, en pruebas independientes, **Then** la reserva histórica conserva su código, estado y mesa, y ocupa el minuto de las 19:00. La solicitud de las 19:01 se confirma con esa mesa.
6. **Given** dos reservas históricas confirmadas para la misma mesa a las 19:00:10 y 19:00:45 de la misma fecha local, **When** se consultan sus códigos y se solicita esa mesa a las 19:00, **Then** ambas conservan sus códigos, estados y asignaciones, y la mesa está ocupada para la nueva solicitud.

### Edge Cases

- Capacidad exacta: una mesa libre con capacidad igual a `partySize` es apta.
- Si `partySize` está entre 1 y 12 y el resto de la solicitud es válido, un catálogo vacío, la falta de mesas libres o un grupo mayor que la capacidad de cualquier mesa producen `REJECTED`.
- Varias mesas aptas: se asigna exactamente una; no se exige una política de preferencia.
- Solicitudes simultáneas para la misma fecha y minuto. Si solo hay una mesa con capacidad suficiente disponible, se confirma exactamente una solicitud y la otra queda rechazada.
- Reserva rechazada: no ocupa mesa ni impide confirmar solicitudes posteriores.
- Datos inválidos y códigos inexistentes son errores; la falta de mesa para una solicitud válida es un resultado de negocio exitoso.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir solicitar y consultar reservas sin autenticación ni cuenta. Nombre y email son datos de contacto, no credenciales.
- **FR-002** — La creación DEBE recibir `customerName`, `customerEmail`, `reservationTime` y `partySize`. Todos son obligatorios. El nombre debe contener texto, el email debe tener formato válido y `partySize` debe cumplir FR-013. `reservationTime` DEBE recibirse como fecha y hora local del restaurante en formato `YYYY-MM-DDTHH:mm`, con fecha y hora válidas. La reserva DEBE guardar un único `reservationTime` a precisión de minuto, sin zona horaria, duración ni hora de finalización. Una entrada inválida DEBE rechazarse antes de crear una reserva o evaluar la disponibilidad.
- **FR-003**: Cada solicitud válida DEBE generar una reserva con un `reservationCode` público, único y estable, conservando los datos del cliente y de la visita para resolverla y consultarla después.
- **FR-004**: La reserva DEBE nacer en `PENDING` y resolverse en el mismo flujo de creación, antes de responder exitosamente. La respuesta DEBE contener código y resultado definitivo `CONFIRMED` o `REJECTED`; `PENDING` es transitorio y no es el resultado de una creación exitosa.
- **FR-005** — Una mesa DEBE considerarse apta si su capacidad es mayor o igual a `partySize` y ninguna reserva confirmada la tiene asignada para la misma fecha, hora y minuto que `reservationTime`.
- **FR-006**: Si existe una mesa apta, el sistema DEBE asignar exactamente una y terminar en `CONFIRMED`. Ninguna reserva puede confirmarse sin `assignedTableId`. No se combinan mesas para una reserva.
- **FR-007**: Si no existe mesa apta, el sistema DEBE terminar en `REJECTED`, sin `assignedTableId`, y comunicarlo como respuesta exitosa de negocio.
- **FR-008** — El sistema DEBE impedir una nueva confirmación para una mesa que ya tenga una reserva confirmada en la misma fecha y minuto, incluso ante solicitudes simultáneas. Solo las reservas confirmadas ocupan la mesa.
- **FR-009**: Los únicos estados DEBEN ser `PENDING`, `CONFIRMED` y `REJECTED`. Las únicas transiciones DEBEN ser `PENDING` → `CONFIRMED` y `PENDING` → `REJECTED`. Los estados finales no cambian ni se reevalúan mediante consulta.
- **FR-010**: El cliente DEBE poder obtener el estado mediante el código, sin email ni datos adicionales. Un código inexistente DEBE producir un error de reserva no encontrada.
- **FR-011**: La feature DEBE exponer los dos casos de uso HTTP definidos abajo, bajo `/api/v1/`, y ser verificable por HTTP sin interfaz gráfica.
- **FR-012**: Los mensajes al cliente DEBEN estar en español. Los errores de validación y reserva no encontrada DEBEN seguir el formato uniforme de errores del proyecto; `REJECTED` pertenece a la respuesta exitosa.
- **FR-013** — Cada nueva solicitud DEBE indicar un `partySize` entero entre 1 y 12 inclusive. Una cantidad fuera del rango o fraccionaria DEBE producir un error de validación antes de crear la reserva o evaluar la disponibilidad. El estado `REJECTED` se reserva para solicitudes válidas que quedan sin mesa.
- **FR-014** — Las reservas existentes DEBEN conservar sus códigos, estados y asignaciones. Su `reservationTime` DEBE corresponder al minuto de llegada en la hora local del restaurante, con los segundos y sus fracciones descartados. Las reservas históricas confirmadas que coincidan en mesa y minuto DEBEN conservarse y ocupar esa mesa para las nuevas solicitudes.

### Tipos de datos solicitados

- `reservationTime` DEBE representarse con `LocalDateTime` y guardarse con segundos y nanosegundos en cero.
- `partySize` DEBE representarse con `Integer` y validarse según FR-013 para nuevas solicitudes.

### Contrato HTTP solicitado

Estos endpoints forman parte del alcance explícito del usuario.

| Operación | Entrada | Resultado exitoso | Error de entrada o consulta |
| --- | --- | --- | --- |
| `POST /api/v1/reservations` | `customerName`, `customerEmail`, `reservationTime` en formato `YYYY-MM-DDTHH:mm` (por ejemplo, `2030-06-12T19:00`) y `partySize` entero de 1 a 12 | Código de reserva y estado final `CONFIRMED` o `REJECTED` como resultados exitosos | Datos obligatorios ausentes o inválidos |
| `GET /api/v1/reservations/{reservationCode}` | Código en la ruta | Estado actual de la reserva identificada | Reserva no encontrada |

### Key Entities *(include if feature involves data)*

- **Reservation**: Solicitud de un grupo para una visita al único restaurante. Contiene `reservationCode` (identificador público), `customerName`, `customerEmail`, `reservationTime`, `partySize`, `status` y `assignedTableId`. La mesa está presente en una reserva confirmada y ausente en una pendiente o rechazada.
- **Mesa existente**: Recurso identificado por un id y una capacidad conocida, usado para evaluar disponibilidad y asignar reservas. Crear o editar mesas pertenece a otra feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

Se definen resultados funcionales verificables con los escenarios de aceptación, sin métricas de negocio, satisfacción, latencia, volumen ni concurrencia.

- **SC-001**: En los casos con mesa apta, el cliente obtiene código y confirmación definitiva en la misma solicitud; cada reserva confirmada tiene exactamente una mesa de capacidad suficiente.
- **SC-002**: En los casos sin mesa apta, el cliente obtiene código y rechazo definitivo como resultado exitoso, sin mesa asignada.
- **SC-003** — Cero nuevas confirmaciones para una mesa ya ocupada en la misma fecha y minuto. En el escenario 7 de la historia 1, cada solicitud para un minuto alternativo se confirma con la misma mesa.
- **SC-004**: Cada código creado en los escenarios devuelve en consultas posteriores la misma decisión final recibida al solicitar la reserva; un código inexistente devuelve reserva no encontrada.
- **SC-005**: En cada caso de entrada inválida de la historia 1 se comunica el error sin crear reservas ni ocupar mesas.
- **SC-006** — Todas las reservas históricas de los escenarios conservan el minuto local de llegada, su código, su estado y su asignación. Cada mesa con reservas históricas confirmadas queda ocupada en el minuto correspondiente.

## Assumptions

- **Dependencia de mesas**: El catálogo y sus capacidades están disponibles como datos preexistentes. Esta feature los consume sin añadir gestión de mesas.
- **Selección de mesa**: Cualquier mesa libre con capacidad suficiente satisface la solicitud. No se exige elegir la de menor capacidad ni combinar mesas.
- **Validación mínima** — Se aplican las reglas de FR-002 y el rango de FR-013 a las nuevas solicitudes. Se aceptan solicitudes sin restricciones de anticipación ni de horario de apertura.
- **Identificación**: El código basta para consultar el estado. El email no se verifica mediante mensajes ni representa una cuenta.
- **Hora local** — Los clientes indican la hora local del restaurante. Para las reservas históricas expresadas como instantes, se toma la hora local del restaurante antes de descartar los segundos y sus fracciones. El plan debe identificar la zona histórica aplicable.

### Límites del alcance

- Backend de reservas de un único restaurante, sin interfaz gráfica ni múltiples locales.
- Sin autenticación ni cuentas de usuario.
- Sin pagos, menú ni pedidos de comida.
- Sin cancelaciones, reprogramaciones ni lista de espera.
- Sin creación ni edición de mesas.
- Sin envío de notificaciones.
- Sin otros estados, transiciones o decisiones posteriores a la respuesta de creación.
- Sin métricas de negocio, satisfacción, tiempos ni objetivos de concurrencia.

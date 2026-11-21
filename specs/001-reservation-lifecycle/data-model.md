# Modelo de datos y asignación

## Reservation

`reservations` es dueño de la tabla `reservation`. Cada fila representa una solicitud válida, incluidas las rechazadas.

| Campo de dominio | Columna | Tipo y reglas |
| --- | --- | --- |
| reservationCode | reservation_code | UUID aleatorio, clave primaria, público e inmutable |
| customerName | customer_name | Texto obligatorio con al menos un carácter distinto de espacio |
| customerEmail | customer_email | Texto obligatorio, no vacío y con formato de email |
| reservationTime | reservation_time, reservation_time_remainder | Instante obligatorio. `timestamptz` conserva hasta seis decimales y la columna de texto guarda los dígitos posteriores, sin ceros finales. La cadena vacía representa una fracción adicional de cero, también para las filas previas a la migración. El DTO recibe fecha y hora con desplazamiento explícito. |
| partySize | party_size | Entero positivo obligatorio. La solicitud HTTP exige de 1 a 12 inclusive. Se conserva `BigInteger` en Java y `numeric` en PostgreSQL para admitir la lectura de registros históricos. |
| status | status | Texto obligatorio restringido a PENDING, CONFIRMED o REJECTED |
| assignedTableId | assigned_table_id | ID numérico opcional que referencia una mesa existente. Presente exactamente cuando el estado es CONFIRMED. |

El DTO exige `@NotBlank` en nombre y email, `@Email` en email, `@NotNull` en la hora y `@NotNull @Positive @Max(12)` en la cantidad. El lector JSON admite números de valor entero, incluidas representaciones como `12.0` y `1.2e1`. El controlador valida antes de llamar al servicio. Una cantidad que supera 12 produce HTTP 400 y la solicitud termina antes de crear una reserva o consultar el catálogo. Una cantidad que cumple el rango y supera la capacidad de todas las mesas produce `REJECTED`.

El esquema existente exige unicidad del código, nulabilidad conforme al modelo, cantidad positiva e integral, valores de estado válidos y equivalencia entre confirmación y mesa asignada. El índice por mesa y hora cubre las reservas confirmadas. El límite de entrada deja consultables los registros históricos de más de 12 y sus asignaciones confirmadas siguen ocupando la mesa durante la franja correspondiente.

## Mesa y franja

`tables` es dueño de `dining_table`, con `id` (bigint, clave primaria) y `capacity` (entero positivo obligatorio). Una mesa puede atender muchas reservas a lo largo del tiempo. Una reserva confirmada usa una sola mesa. El enlace se modela como una clave foránea escalar para mantener la separación entre dominios.

La franja se deriva de `reservationTime` sumando 90 minutos al instante inicial. Usar un intervalo cerrado al inicio y abierto al final. Hay conflicto si **inicio existente < fin solicitado** e **inicio solicitado < fin existente**. Con duración fija, el repositorio puede buscar inicios estrictamente entre el inicio solicitado menos 90 minutos y el fin solicitado, en las mesas candidatas y con estado CONFIRMED. Cuando los microsegundos coinciden, la comparación conserva el orden de los dígitos restantes. Las reservas rechazadas nunca ocupan capacidad.

## Transiciones y transacción

| Estado inicial | Condición | Resultado |
| --- | --- | --- |
| PENDING | Existe una candidata libre | Asignar su ID y pasar a CONFIRMED |
| PENDING | Se agotaron las candidatas | Pasar a REJECTED con mesa nula |

Los dos resultados son terminales. `PENDING` vive dentro del flujo de creación. El repositorio guarda la decisión final antes de responder. Un fallo técnico revierte la transacción y se comunica como error del sistema.

1. El servicio de reservas abre una transacción PostgreSQL READ COMMITTED. Todos los asignadores siguen este protocolo.
2. El servicio llama a `tables.service.TableCatalog.lockCandidates(partySize)` dentro de la misma transacción. El repositorio de mesas compara la capacidad con el tamaño completo del grupo y materializa los IDs con capacidad suficiente en orden ascendente por ID inmutable mediante `SELECT … ORDER BY id FOR UPDATE`. Espera por las filas bloqueadas y mantiene los bloqueos hasta el fin de la transacción.
3. En una **segunda sentencia**, el repositorio de reservas lee las ocupaciones de esas mesas que se superponen. Así incorpora los commits producidos durante la espera. Candidatas vacías o todas ocupadas producen un rechazo. Cualquier candidata libre permite confirmar.
4. Persistir la reserva final y hacer commit. Una espera por bloqueo o un fallo de escritura nunca se interpreta como falta de mesa. La consulta por código solo lee el estado definitivo guardado en la base.

# Modelo de datos y asignación

## Reservation

El módulo `reservations` es dueño de la tabla `reservation`. Cada fila representa una solicitud válida bajo las reglas de su versión, incluidas las rechazadas.

| Campo | Columna | Tipo y reglas |
| --- | --- | --- |
| `reservationCode` | `reservation_code` | UUID aleatorio, clave primaria, público e inmutable |
| `customerName` | `customer_name` | Texto obligatorio con al menos un carácter distinto de espacio |
| `customerEmail` | `customer_email` | Texto obligatorio con formato de email |
| `reservationTime` | `reservation_time` | `LocalDateTime` obligatorio y `timestamp without time zone` en PostgreSQL. La fecha es finita y los segundos y sus fracciones son cero. |
| `partySize` | `party_size` | `Integer` obligatorio y `integer` positivo en PostgreSQL. El DTO exige de 1 a 12 inclusive para nuevas solicitudes. |
| `status` | `status` | `PENDING`, `CONFIRMED` o `REJECTED` |
| `assignedTableId` | `assigned_table_id` | Clave foránea escalar a `dining_table.id`, presente exactamente cuando el estado es `CONFIRMED` |

El DTO conserva Bean Validation con `@NotBlank` y `@Email` para los datos de contacto y `@NotNull` para la llegada. El lector de llegada exige texto con forma exacta `YYYY-MM-DDTHH:mm` y un valor de calendario válido. El lector de cantidad exige un número JSON de valor entero y entrega un `Integer` sujeto a `@NotNull @Positive @Max(12)`. Se conservan formas numéricas integrales como `12.0` y `1.2e1`. Los valores enormes se rechazan antes de consultar disponibilidad, sin expandir exponentes. El resto de las reglas del contrato se valida en el controlador antes de delegar al servicio.

La escritura y la lectura de `reservation_time`, incluido el parámetro de búsqueda, usan enlace JDBC directo de `LocalDateTime`. Activar `spring.jpa.properties.hibernate.type.java_time_use_direct_jdbc=true` y verificar el comportamiento con una zona JVM distinta. La comprobación de base de datos exige una hora finita e igual a su truncamiento al minuto.

## Mesa y disponibilidad por minuto

El módulo `tables` es dueño de `dining_table`. La mesa tiene un `id` de tipo `bigint` y una `capacity` de tipo `integer` positivo. Cada reserva confirmada ocupa una mesa en su fecha y minuto local. Una mesa puede atender reservas en minutos distintos.

1. El servicio crea la reserva en `PENDING` y abre la asignación bajo `READ COMMITTED`.
2. `TableCatalog.lockCandidates(int partySize)` obtiene las mesas suficientes y bloquea sus filas por ID ascendente, dentro de la misma transacción.
3. Una sentencia posterior consulta las confirmaciones de esas mesas mediante igualdad con `reservation_time`. El resultado usa IDs distintos y conserva el índice parcial no único `reservation_occupied_table_time_idx` por mesa y hora.
4. El servicio elige una mesa libre y guarda `CONFIRMED` con su ID o guarda `REJECTED` con mesa nula. La transacción mantiene los bloqueos hasta commit o rollback. La consulta por código devuelve el estado final.

## Migración desde V3

- Flyway aplica V4 en una transacción. Configurar `BISTRO_LEGACY_RESERVATION_ZONE` con la zona IANA histórica del restaurante. Spring pasa el valor al placeholder `restaurant_zone` de Flyway. El valor predeterminado del plan es `America/Bogota`.
- Bloquear las escrituras antes de comprobar y convertir. Validar la zona, la finitud de las fechas y que cada cantidad sea un entero positivo representable por `integer`. Si falla una comprobación, abortar con un diagnóstico antes de cambiar los datos.
- Convertir `reservation_time` con la expresión `date_trunc('minute', reservation_time AT TIME ZONE '<zona>')`, cambiar el tipo y retirar `reservation_time_remainder`. Cambiar `party_size` a `integer` y aplicar la comprobación de positividad. Verificar que el índice parcial queda operativo sobre la columna convertida.
- Conservar códigos, estados, mesas y cantidades históricas representables. Dos confirmaciones que pasan a la misma mesa y minuto permanecen consultables y ocupan la mesa en ese minuto. Con la zona predeterminada, los instantes `2030-06-13T00:00:10Z` y `2030-06-13T00:00:45Z` se guardan como `2030-06-12T19:00`.

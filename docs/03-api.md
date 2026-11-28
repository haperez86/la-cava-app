# 03 · Contrato de la API — La Cava

Base `/api/v1`. JSON. `Authorization: Bearer <jwt>` salvo login y health. Errores `ProblemDetail` con `detail` en español y `errors[]` en validaciones. Listados paginados `?page&size&sort`.
Roles: **A** Administrador · **C** Cajero · **M** Mesero · **P** Preparación.

## Sistema
| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/health` | público | `{status, db}`; 200 con `UP` si la base responde, 503 con `db: DOWN` si no |

## Auth
| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/auth/login` | público | `{username, password}` → `{token, expiresAt, user:{id, fullName, role}}` |
| GET | `/auth/me` | todos | |

## Menú
| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/products` | todos | Filtros `q`, `categoryId`, `state` (`ON_MENU`, `OUT_OF_STOCK`, `SEASONAL`, `OUT_OF_SEASON`, `INACTIVE`). Cada producto trae `available` (vigente), `orderable` (bool) y `blockReason` |
| POST | `/products` | A | `{name, categoryName, price, description, icon, minStock, seasonName?, seasonUntil?, initialLot?:{quantity, expiresOn}}` |
| PUT | `/products/{id}` | A | |
| PATCH | `/products/{id}/active` | A | `{active}` |
| GET | `/categories` | todos | `[{id, name}]` ordenado por nombre |

Respuesta de producto: `{id, name, categoryId, categoryName, price, description, icon, minStock, active, seasonName, seasonUntil, available}`; `GET /products` devuelve `{content, page, size, totalElements, totalPages}` (`page` desde 0; `sort` admite `name`, `price`, `minStock`, `createdAt`). Desde HU-03 cada producto trae además `state` (`ON_MENU`, `OUT_OF_STOCK`, `OUT_OF_SEASON`, `INACTIVE`), `orderable` y `blockReason` ("Retirado de la carta", "Fuera de temporada", "Agotado" o `null`); el filtro `state` admite también `SEASONAL` y un valor inválido → 400 (D31). Nombre duplicado → 409 "Ya existe un producto con ese nombre.".

## Inventario
| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/inventory` | A | Productos con vigente, mínimo, próximo vencimiento, unidades vencidas y estado. Filtros `q`, `categoryId`, `state` (`AVAILABLE`, `LOW`, `OUT`, `EXPIRING`, `HAS_EXPIRED`, `OUT_OF_SEASON`), `season` |
| GET | `/inventory/summary` | A | Contadores: agotados, stock bajo, por vencer, unidades vencidas, y temporadas para el filtro |
| GET | `/products/{id}/lots` | A | Lotes con estado (vigente, vence pronto, vencido, consumido) |
| POST | `/inventory/lots` | A | `{productId, quantity, expiresOn, seasonName?}` → crea el lote y el movimiento `LOT_IN` |
| POST | `/inventory/outputs` | A | `{productId, quantity, type, note}` → consumo FEFO |
| POST | `/inventory/expired/write-off` | A | Da de baja todo lo vencido → `{lots, units}` |
| GET | `/inventory/movements` | A | Filtros `productId`, `from`, `to`; paginado |

Lote (`LotResponse`): `{id, productId, productName, quantityReceived, quantityAvailable, expiresOn, seasonName, receivedAt, status}`; `status` = `CONSUMED` (cantidad 0) → `EXPIRED` (vencido con cantidad) → `EXPIRING` (vence en ≤ 3 días) → `ACTIVE`. `POST /inventory/lots` responde 201 con el lote; `seasonName` vacío o nulo toma la temporada del producto; validación → 400 `errors[]` (`productId`, `quantity`, `expiresOn`, `seasonName`); producto inexistente → 404 (D32).
Salida (`POST /inventory/outputs`): `{productId, quantity, type, note}` con `type` = `WASTE_OUT`, `DAMAGED_OUT`, `INTERNAL_USE_OUT` o `ADJUSTMENT_OUT` y `note` obligatoria (≤ 140) → `{productId, productName, type, quantity, consumed:[{lotId, expiresOn, quantity}], available}`; cantidad mayor que la vigente → 400 en `quantity` ("Solo hay N disponibles."). Baja de vencidos → `{lots, units}`. Cualquier cantidad decimal en un campo entero → 400 en ese campo (D33).
Fila de inventario (`GET /inventory`, paginado): `{productId, name, icon, categoryId, categoryName, seasonName, seasonUntil, available, minStock, nextExpiresOn, daysToExpiry, expiredUnits, state, hasExpired}`; solo productos activos; `sort` admite `name`, `available`, `minStock`. Resumen (`GET /inventory/summary`): `{outOfStock, lowStock, expiring, expiredUnits, seasons}`. Movimiento (`GET /inventory/movements`, paginado, más reciente primero): `{id, createdAt, productId, productName, lotId, type, quantity, note, orderId, userName}`; `from`/`to` son fechas de Bogotá inclusivas y `from` > `to` → 400 en `from` (D34).

## Mesas
| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/tables` | A, C, M | Mesas con estado (`FREE`/`OCCUPIED`), etapa (`READY`, `IN_PREPARATION`, `PENDING`, `SERVED`), número de pedidos, total y último mesero |
| POST | `/tables/resolve` | M, A, C | `{code}` (contenido del QR) o `{number, pin}` → mesa con su cuenta abierta. 404 si el código es inválido o la mesa está inactiva |
| GET | `/tables/{id}/account` | A, C | Pedidos abiertos con ítems y total. El mesero solo la ve vía `resolve` (403 aquí) |
| POST | `/tables` | A | `{number}` |
| PATCH | `/tables/{id}/active` | A | |
| POST | `/tables/{id}/rotate-token` | A | Invalida el QR anterior |
| GET | `/tables/qr-sheet` | A | Datos para imprimir los QR: `[{number, code, pin}]` (el frontend dibuja los QR) |

Mesa: `{id, number, active, status, stage, openOrders, total, waiterName}`; `status` = `FREE`/`OCCUPIED` según haya pedidos abiertos (`PENDING`, `IN_PREPARATION`, `READY`, `DELIVERED`); `stage` = la etapa más urgente (`READY` > `IN_PREPARATION` > `PENDING` > `SERVED`, todos entregados) o `null`; `waiterName` = mesero del pedido abierto más reciente (HU-09). `POST /tables`: número entero > 0 (400 `errors[]`), repetido → 409 "Ya existe una mesa con ese número.". `resolve` con `{code}` o `{number, pin}` (PIN como texto de 4 dígitos) → `{id, number, account:{orders, total}}`; cualquier código, PIN o mesa inválidos o inactivos → 404 "Código de mesa inválido"; sin datos → 400. `qr-sheet` solo trae mesas activas. `resolve` devuelve además `tablePass` y `passExpiresAt` (comprobante de mesa de 4 h, D37); `account.orders` son los pedidos abiertos de la mesa con el formato de `OrderResponse`.

## Pedidos
| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/orders` | M, C, A | `{tableId, waiterId?, items:[{productId, quantity, note?}], note?, tableCode?}`. Mesero: `waiterId` se ignora (es él) y `tableCode` del QR es **obligatorio**. 422 por existencia insuficiente o producto no disponible |
| GET | `/orders` | A, C | Filtros `status`, `q` (número, mesa o mesero), fechas; paginado |
| GET | `/orders/summary` | A, C | Contadores `{all, PENDING, IN_PREPARATION, READY, DELIVERED, CLOSED, CANCELLED}` con los mismos `q`/`from`/`to`, ignorando `status` (D39) |
| GET | `/orders/mine` | M | Pedidos propios no entregados (`PENDING`, `IN_PREPARATION`, `READY`): arreglo de `OrderResponse`, del más antiguo al más reciente (HU-14, D42) |
| GET | `/orders/{id}` | todos | El mesero solo los suyos |
| POST | `/orders/{id}/start` | P, A | `PENDING → IN_PREPARATION`; guarda `startedAt`; devuelve el `OrderResponse` (HU-12, D40) |
| POST | `/orders/{id}/ready` | P, A | `IN_PREPARATION → READY`; guarda `readyAt`; devuelve el `OrderResponse`. En la misma transacción crea el aviso `ORDER_READY` del mesero y, tras el commit, lo envía por SSE solo a sus conexiones (HU-14, D42) |
| POST | `/orders/{id}/notify-again` | P, A | Solo en `READY` (otro estado → 409): crea un aviso nuevo igual y lo envía al mesero; 204 sin cuerpo (HU-14, D42) |
| POST | `/orders/{id}/deliver` | M (propio), A | `READY → DELIVERED`; guarda `delivered_at`, marca leídos sus avisos y emite `order-changed`; devuelve el `OrderResponse`. Ajeno para el mesero → 404; fuera de `READY` → 409 (HU-14, D42) |
| POST | `/orders/{id}/cancel` | A, C | `{reason}`; solo `PENDING`; devuelve el stock a sus lotes |
| GET | `/kitchen/board` | P, A | Pedidos `PENDING`, `IN_PREPARATION` y `READY` con ítems: arreglo de `OrderResponse`, sin paginar (D40) |

Pedido (`OrderResponse`): `{id, tableId, tableNumber, waiterId, waiterName, status, note, createdAt, items:[{id, productId, productName, unitPrice, quantity, note, subtotal}], total, cancelReason, cancelledAt, startedAt, readyAt}` (`cancelReason` y `cancelledAt` solo con valor si está `CANCELLED`; `startedAt` y `readyAt` son `null` hasta que el pedido pasa a `IN_PREPARATION` y `READY`). `GET /kitchen/board` devuelve un arreglo de `OrderResponse` ordenado por estado (`PENDING` → `IN_PREPARATION` → `READY`) y, dentro de cada estado, del más antiguo al más reciente; `POST /orders/{id}/start` y `/ready` (P, A): transición inválida o doble clic (p. ej. `start` sobre un pedido ya iniciado) o conflicto de `@Version` → 409, inexistente → 404, mesero/cajero → 403 (HU-12, D40). `GET /orders`: más reciente primero; `q` numérico busca por número de pedido o de mesa, de texto por nombre del mesero; `from`/`to` en hora de Bogotá; `status` inválido o `from` > `to` → 400. `POST /orders/{id}/cancel`: `{reason}` de 1 a 120 caracteres sin espacios sobrantes (400 en `reason`); fuera de `PENDING` → 409 "Solo se puede cancelar un pedido pendiente."; inexistente → 404; devuelve el `OrderResponse` cancelado (D39). En `POST /orders` el mesero envía `tableCode` = `tablePass` de `resolve`; si falta, es de otra mesa o de otro mesero, venció, se regeneró el QR o la mesa está inactiva → 403 "Escanea de nuevo el QR de la mesa.". Cajero/Admin: `waiterId` de un mesero activo (400 si no); mesa inexistente → 404, inactiva → 422. Notas: ítem ≤ 80, general ≤ 140; cantidad entera de 1 a 999 por ítem. Producto inexistente → 404. Existencia insuficiente o producto no disponible → 422 con `productId`, `productName`, `available` (y `reason` si no está disponible); no se guarda nada. `GET /orders/{id}`: un pedido ajeno para el mesero → 404 (D38).

## Notificaciones (tiempo real)
| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/notifications/stream-ticket` | todos | → `{ticket}` de un solo uso y 60 s de vigencia (porque `EventSource` no envía el header Authorization) |
| GET | `/notifications/stream?ticket=…` | todos | `text/event-stream`. Eventos: `notification` (aviso para el usuario), `order-changed` (`{orderId, tableId, status}`, para refrescar vistas) y `heartbeat` cada 25 s |
| GET | `/notifications/unread` | todos | Avisos no leídos del usuario autenticado, del más antiguo al más reciente: arreglo de `{id, type, orderId, tableNumber, message, createdAt, readAt}` |
| POST | `/notifications/{id}/read` | todos | Marca leído un aviso propio; ajeno o inexistente → 404; uno ya leído no falla (200 sin cuerpo) |

HU-13 (D41): ticket aleatorio de un solo uso; un ticket vencido, reutilizado, inexistente o de un usuario inactivo → 401 `ProblemDetail`. El stream envía un comentario `:connected` al abrir y `order-changed` a todos los conectados solo después del commit (crear, iniciar, marcar listo y cancelar).
HU-14 (D42): `ready` y `notify-again` guardan en `notification` un aviso `ORDER_READY` con el mensaje "Pedido #N listo · Mesa M" y, tras el commit, lo envían como evento SSE `notification` solo a las conexiones del mesero del pedido. Forma del aviso (evento y `unread`): `{id, type: "ORDER_READY"|"ORDER_CREATED", orderId, tableNumber, message, createdAt, readAt}` (`readAt` es `null` hasta que se lee). Los avisos `ORDER_CREATED` a Preparación llegan con HU-15.

Al reconectar, el cliente pide un ticket nuevo y vuelve a consultar `unread` y los datos de la vista.

## Caja y facturación
| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/cash/current` | A, C | Turno abierto con resumen o 204 |
| POST | `/cash/sessions` | A, C | `{openingAmount}` |
| POST | `/cash/current/movements` | A, C | `{type, amount, reason}` |
| PUT | `/cash/current/count` | A, C | `{counts:[{denomination, quantity}]}` |
| POST | `/cash/current/close` | A, C | |
| GET | `/billing/open-accounts` | A, C | Mesas con cuenta abierta: ítems consolidados, total y pedidos no entregados |
| POST | `/billing/tables/{tableId}/pay` | A, C | `{paymentMethod, receivedAmount?, approvalCode?, customerName?, customerDoc?, customerEmail?}` → factura. 409 sin caja abierta o sin saldo; 422 por validaciones |
| GET | `/billing/bills` | A, C | Filtros `from`, `to`; por defecto hoy |
| GET | `/billing/bills/{number}` | A, C | Detalle para imprimir |

## Informes
| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/reports/dashboard` | A, C | Indicadores del día; para A también el producto top del mes y las alertas |
| GET | `/reports/monthly?month=YYYY-MM` | A | Indicadores, top productos, medios de pago y detalle de cuentas |
| GET | `/reports/monthly.xlsx?month=YYYY-MM` | A | Excel con hojas Resumen, Top productos y Detalle |

## Usuarios
`GET /users`, `POST /users`, `PATCH /users/{id}/active`, `POST /users/{id}/password` — solo A. `GET /users/waiters` (A, C): meseros activos para asignar pedidos.

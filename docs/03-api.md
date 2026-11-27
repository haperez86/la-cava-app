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
| GET | `/inventory/summary` | A | Contadores: agotados, stock bajo, por vencer, unidades vencidas |
| GET | `/products/{id}/lots` | A | Lotes con estado (vigente, vence pronto, vencido, consumido) |
| POST | `/inventory/lots` | A | `{productId, quantity, expiresOn, seasonName?}` → crea el lote y el movimiento `LOT_IN` |
| POST | `/inventory/outputs` | A | `{productId, quantity, type, note}` → consumo FEFO |
| POST | `/inventory/expired/write-off` | A | Da de baja todo lo vencido → `{lots, units}` |
| GET | `/inventory/movements` | A | Filtros `productId`, `from`, `to`; paginado |

Lote (`LotResponse`): `{id, productId, productName, quantityReceived, quantityAvailable, expiresOn, seasonName, receivedAt, status}`; `status` = `CONSUMED` (cantidad 0) → `EXPIRED` (vencido con cantidad) → `EXPIRING` (vence en ≤ 3 días) → `ACTIVE`. `POST /inventory/lots` responde 201 con el lote; `seasonName` vacío o nulo toma la temporada del producto; validación → 400 `errors[]` (`productId`, `quantity`, `expiresOn`, `seasonName`); producto inexistente → 404 (D32).
Salida (`POST /inventory/outputs`): `{productId, quantity, type, note}` con `type` = `WASTE_OUT`, `DAMAGED_OUT`, `INTERNAL_USE_OUT` o `ADJUSTMENT_OUT` y `note` obligatoria (≤ 140) → `{productId, productName, type, quantity, consumed:[{lotId, expiresOn, quantity}], available}`; cantidad mayor que la vigente → 400 en `quantity` ("Solo hay N disponibles."). Baja de vencidos → `{lots, units}`. Cualquier cantidad decimal en un campo entero → 400 en ese campo (D33).

## Mesas
| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/tables` | A, C, M | Mesas con estado (`FREE`/`OCCUPIED`), etapa (`READY`, `IN_PREPARATION`, `PENDING`, `SERVED`), número de pedidos, total y último mesero |
| POST | `/tables/resolve` | M, A, C | `{code}` (contenido del QR) o `{number, pin}` → mesa con su cuenta abierta. 404 si el código es inválido o la mesa está inactiva |
| GET | `/tables/{id}/account` | A, C, M | Pedidos abiertos con ítems y total. El mesero solo vía `resolve` |
| POST | `/tables` | A | `{number}` |
| PATCH | `/tables/{id}/active` | A | |
| POST | `/tables/{id}/rotate-token` | A | Invalida el QR anterior |
| GET | `/tables/qr-sheet` | A | Datos para imprimir los QR: `[{number, code, pin}]` (el frontend dibuja los QR) |

## Pedidos
| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/orders` | M, C, A | `{tableId, waiterId?, items:[{productId, quantity, note?}], note?, tableCode?}`. Mesero: `waiterId` se ignora (es él) y `tableCode` del QR es **obligatorio**. 422 por existencia insuficiente o producto no disponible |
| GET | `/orders` | A, C | Filtros `status`, `q` (número, mesa o mesero), fechas; paginado |
| GET | `/orders/mine` | M | Pedidos propios no entregados |
| GET | `/orders/{id}` | todos | El mesero solo los suyos |
| POST | `/orders/{id}/start` | P, A | `PENDING → IN_PREPARATION` |
| POST | `/orders/{id}/ready` | P, A | `IN_PREPARATION → READY`; crea un aviso para el mesero |
| POST | `/orders/{id}/notify-again` | P, A | Solo en `READY` |
| POST | `/orders/{id}/deliver` | M (propio), A | `READY → DELIVERED`; marca leídos sus avisos |
| POST | `/orders/{id}/cancel` | A, C | `{reason}`; solo `PENDING`; devuelve el stock a sus lotes |
| GET | `/kitchen/board` | P, A | Pedidos `PENDING`, `IN_PREPARATION` y `READY` con ítems |

## Notificaciones (tiempo real)
| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/notifications/stream-ticket` | todos | → `{ticket}` de un solo uso y 60 s de vigencia (porque `EventSource` no envía el header Authorization) |
| GET | `/notifications/stream?ticket=…` | todos | `text/event-stream`. Eventos: `notification` (aviso para el usuario), `order-changed` (`{orderId, tableId, status}`, para refrescar vistas) y `heartbeat` cada 25 s |
| GET | `/notifications/unread` | todos | Avisos no leídos del usuario |
| POST | `/notifications/{id}/read` | todos | |

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

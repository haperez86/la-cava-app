# 04 · Backlog — La Cava

Prefijo **LCV**. Implementar **en este orden**. Estado: `⬜ Pendiente` · `🔄 En curso` · `✅ Hecha` · `⛔ Bloqueada`.
Cada criterio de aceptación (CA) debe quedar cubierto por al menos una prueba automatizada.

---

## Épica 0 · Fundaciones

### LCV-HU-00 · Esqueleto del proyecto ✅
- Backend Spring Boot 4.1 / Java 21 con Flyway, JPA, Security, Validation y springdoc; `Clock` inyectable; perfiles `local` y `test`; `GET /api/v1/health`.
- Manejo global de errores con `ProblemDetail`.
- Frontend Angular 22 con layout de la demo (barra lateral en escritorio y navegación inferior en móvil), tokens de diseño, toasts y proxy `/api` (con buffering desactivado para SSE).
- **CA1** `./mvnw verify` y `npm test` pasan con PostgreSQL local.
- **CA2** El frontend muestra el layout y consume `/health`.
- **Desviaciones:** migración `V1__baseline.sql` sin tablas; JWT (oauth2-resource-server) diferido a HU-01; ícono de menú como glifo de texto y pantalla placeholder `/inicio`; toast `ready` y tokens `--gold-ink`, `--brand-sub`, `--brand-line`, `--side-width` añadidos. Pendientes menores del revisor para HU-01: prueba del 503 de health, estilos de `toast-container` con breakpoint/token, failsafe sobrante en `pom.xml`.

### LCV-HU-01 · Inicio de sesión y menú por rol ✅
- **CA1** Con credenciales válidas recibo el JWT y entro a la pantalla inicial de mi rol: A y C → Dashboard; M → Mis mesas; P → Comandas.
- **CA2** Credenciales inválidas o usuario inactivo → "Usuario o contraseña incorrectos".
- **CA3** Cada rol ve solo sus opciones (especificación §1) y las rutas ajenas lo redirigen a su pantalla inicial.
- **CA4** Endpoints fuera de su rol → 403.
- **Desviaciones:** migración nueva `V2__create_app_user.sql`; la respuesta de login y `/auth/me` no incluye `username` (según docs/03); el 403 de CA4 se prueba con un controller solo de pruebas porque aún no hay endpoints por rol; pantallas `/dashboard`, `/mesas` y `/preparacion` son placeholders. Pendientes menores: refrescar el rol con `/auth/me` al arrancar (HU-21) y mover `core/auth/testing.ts` a un helper solo de pruebas.

---

## Épica 1 · Menú

### LCV-HU-02 · Productos y categorías ✅
- **CA1** Creo y edito productos; un nombre duplicado → error; las categorías nuevas se crean al vuelo.
- **CA2** Temporada: si indico nombre de temporada, la fecha "disponible hasta" es obligatoria.
- **CA3** Activo o desactivo productos (retirar o volver a la carta).
- **CA4** Al crear puedo indicar un lote inicial (cantidad y vencimiento); si hay cantidad, el vencimiento es obligatorio y ≥ hoy.
- **Desviaciones:** migración V3 crea también `stock_lot` y `stock_movement` (D29); validación de temporada y lote en el servicio con el mismo 400 `errors[]`; una carrera de nombres duplicados responde el 409 genérico; pantalla `/menu` con tarjetas y formulario modal (hoja inferior en móvil). Pendientes menores: inyectar `ZoneId` en el seeder y crear producto y lotes de ejemplo en una transacción; 409 que no sea de nombre → toast; cantidad de lote entera en el formulario; prueba de `sort` anidado → 400.

### LCV-HU-03 · Disponibilidad y filtros del menú ⬜
- **CA1** `orderable` es verdadero solo si el producto está activo, en temporada y con existencia vigente > 0; si no, `blockReason` dice *Retirado de la carta*, *Fuera de temporada* o *Agotado*.
- **CA2** Filtros por texto, categoría y estado (En la carta, Agotados, De temporada, Fuera de temporada, Inactivos).
- **CA3** Con un `Clock` fijo: un producto cuya temporada terminó ayer aparece como *Fuera de temporada*.

---

## Épica 2 · Inventario por lotes

### LCV-HU-04 · Entradas por lote ⬜
- **CA1** Registro un lote con cantidad > 0, vencimiento ≥ hoy y temporada opcional (por defecto la del producto); se crea el movimiento `LOT_IN`.
- **CA2** La existencia vigente excluye los lotes vencidos.

### LCV-HU-05 · Consumo FEFO, salidas y baja de vencidos ⬜
- **CA1** El consumo descuenta primero del lote que vence antes y puede repartirse entre varios lotes.
- **CA2** Una salida manual (merma, dañado, consumo interno, ajuste) exige observación y cantidad ≤ vigente.
- **CA3** "Dar de baja vencidos" deja en 0 los lotes vencidos y registra un movimiento por lote.

### LCV-HU-06 · Vista de inventario y alertas ⬜
- **CA1** Contadores: agotados, stock bajo, por vencer (≤ 3 días) y unidades vencidas.
- **CA2** La tabla muestra temporada, vigente, mínimo, próximo vencimiento ("vence hoy", "vence mañana", "en N días"), unidades vencidas y estado.
- **CA3** Filtros por texto, categoría, estado y temporada.
- **CA4** El detalle de lotes y los movimientos recientes son visibles.

---

## Épica 3 · Mesas y QR

### LCV-HU-07 · Mesas y códigos QR ⬜
- **CA1** El administrador crea y desactiva mesas; cada mesa recibe un token HMAC y un PIN de 4 dígitos.
- **CA2** Hoja de QR imprimible (número de mesa, QR y PIN debajo).
- **CA3** Regenerar el token invalida el QR anterior (`resolve` del código viejo → 404).

### LCV-HU-08 · Escanear QR y abrir mesa (mesero) ⬜
- **CA1** "Escanear QR de mesa" abre la cámara (`BarcodeDetector` o `@zxing/browser`) y, al leer un código válido, carga la mesa con su cuenta.
- **CA2** Sin cámara puedo escribir el número de mesa y el PIN.
- **CA3** Código inválido, PIN errado o mesa inactiva → "Código de mesa inválido".
- **CA4** En "Mis mesas" las mesas se ven pero **no** se pueden abrir tocándolas.

### LCV-HU-09 · Estado de mesas ⬜
- **CA1** Cada mesa muestra Disponible/Ocupada y, si está ocupada, la etapa más urgente (Listo para recoger > En preparación > Pendiente > Servido) resaltada, junto con el número de pedidos, el total y el mesero.
- **CA2** La leyenda de estados es visible.

---

## Épica 4 · Pedidos

### LCV-HU-10 · Crear pedido ⬜
- **CA1** El modal de pedido tiene búsqueda y pestañas de categoría que **sí filtran**, una lista compacta con −/+ y una **barra inferior fija** con unidades, total y "Enviar a preparación" (no hay que desplazarse para enviar).
- **CA2** Los productos no disponibles se ven deshabilitados con su motivo; no puedo superar la existencia vigente.
- **CA3** Observaciones por ítem y general.
- **CA4** El mesero crea pedidos solo con el `tableCode` del QR y queda asignado; el Cajero y el Administrador eligen mesa y mesero.
- **CA5** Al guardar se consumen los lotes (FEFO, con `order_item_lot`) y el pedido queda `PENDING`; si un producto no alcanza (p. ej. otra mesa lo pidió antes), 422 y no se guarda nada.

### LCV-HU-11 · Listado de pedidos y cancelación ⬜
- **CA1** Listado con filtros por estado (con contadores) y búsqueda por número, mesa o mesero.
- **CA2** Cancelar (A, C) solo en `PENDING` y con motivo; las cantidades vuelven a sus lotes.
- **CA3** Transiciones inválidas → 409.

---

## Épica 5 · Preparación y tiempo real

### LCV-HU-12 · Tablero de preparación ⬜
- **CA1** Veo los pedidos `PENDING`, `IN_PREPARATION` y `READY` con mesa, mesero, ítems, observaciones y hora.
- **CA2** "Iniciar preparación" y "Listo · avisar a <mesero>" cambian el estado.
- **CA3** "Imprimir comanda" genera la tirilla de 80 mm.

### LCV-HU-13 · Canal SSE ⬜
- **CA1** Con un ticket válido me conecto a `/notifications/stream`; un ticket vencido o reutilizado → 401.
- **CA2** Recibo `heartbeat` cada 25 s; si se cae la conexión, el cliente reconecta con backoff y refresca los datos.
- **CA3** Los eventos se emiten solo **después del commit** de la transacción que los origina.

### LCV-HU-14 · Aviso al mesero de pedido listo ⬜
- **CA1** Cuando Preparación marca listo, el mesero del pedido recibe al instante el toast "Pedido #N listo · Mesa M", la vibración, el contador en la campana y el ítem en "Listos para recoger". Los demás meseros no lo reciben.
- **CA2** Si estaba desconectado, al volver ve sus avisos no leídos.
- **CA3** "Recogido y entregado" pasa el pedido a `DELIVERED` y marca leídos sus avisos.
- **CA4** "Reenviar aviso" genera un aviso nuevo.

### LCV-HU-15 · Aviso a Preparación y refresco automático ⬜
- **CA1** Al crear un pedido, los usuarios de Preparación conectados reciben "Nuevo pedido #N · Mesa M" y el tablero se actualiza solo.
- **CA2** Mesas, caja y tablero se actualizan ante eventos `order-changed` sin recargar la página.

---

## Épica 6 · Caja y facturación

### LCV-HU-16 · Turno de caja, movimientos, arqueo y cierre ⬜
- **CA1** Abrir turno (uno a la vez), registrar movimientos con concepto, conteo por denominación, diferencia y resultado, y cierre.
- **CA2** El efectivo esperado cuenta solo las facturas en efectivo **del turno**.

### LCV-HU-17 · Cobrar cuenta de mesa ⬜
- **CA1** Sin caja abierta → no puedo cobrar y se me ofrece ir a abrirla.
- **CA2** La cuenta muestra los ítems consolidados, el total y la advertencia "N sin entregar" si aplica.
- **CA3** Efectivo con recibido < total → error; datáfono sin aprobación → error.
- **CA4** Al cobrar se crea la factura `LC-AAAA-00001` con sus líneas, todos los pedidos abiertos de la mesa pasan a `CLOSED` y la mesa queda Disponible, todo en una transacción.

### LCV-HU-18 · Factura 80 mm y reimpresión ⬜
- **CA1** Tras cobrar veo "Pago registrado" con "Imprimir factura" (iframe oculto, sin ventana emergente).
- **CA2** La factura contiene los campos de la especificación §7.
- **CA3** "Facturas emitidas hoy" en Caja y el detalle en Informes permiten reimprimir.

---

## Épica 7 · Informes y dashboard

### LCV-HU-19 · Dashboard ⬜
- **CA1** Ventas cobradas hoy, pedidos activos (y cuántos listos), mesas ocupadas y por cobrar.
- **CA2** Para el Administrador, además: producto más vendido del mes, agotados, por vencer y con lotes vencidos (con acceso al inventario filtrado).

### LCV-HU-20 · Informe mensual y Excel ⬜
- **CA1** Por mes: pedidos, consumo, recaudado, saldo por cobrar, ranking de más vendidos y medios de pago.
- **CA2** Detalle de cuentas con reimpresión de factura.
- **CA3** Exportación `.xlsx` con hojas Resumen, Top productos y Detalle.

---

## Épica 8 · Usuarios

### LCV-HU-21 · Gestión de usuarios ⬜
- **CA1** Crear (usuario único, rol, contraseña de mínimo 8 caracteres), activar o desactivar y restablecer contraseña.
- **CA2** No se puede desactivar el último administrador activo.
- **CA3** Los meseros inactivos no aparecen para asignar pedidos ni pueden iniciar sesión.

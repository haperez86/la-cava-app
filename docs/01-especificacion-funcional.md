# 01 · Especificación funcional — La Cava

Restaurante/bar de copas y tablas con 12 mesas (configurable). Los meseros toman pedidos en el celular escaneando el QR de la mesa; Preparación recibe las comandas y avisa cuando están listas; el cajero cobra la cuenta de la mesa e imprime la factura; el administrador gestiona menú, inventario por lotes, informes y usuarios. Demo de referencia: `demo/index.html`.

## 1. Roles

| Rol | Puede |
|---|---|
| **Administrador** | Todo. |
| **Cajero** | Dashboard, ver mesas y cuentas, crear pedidos para cualquier mesa (asignando mesero), cobrar, facturas y reimpresión, caja (apertura, movimientos, arqueo, cierre). |
| **Mesero** | Solo "Mis mesas": escanear el QR de una mesa, ver su cuenta, asignarle pedidos, recibir avisos de pedidos listos y confirmar la entrega. **No puede abrir una mesa sin escanear su QR.** Ve sus pedidos en curso. |
| **Preparación** | Tablero de comandas: iniciar preparación, marcar listo (avisa al mesero), reenviar aviso, imprimir comanda. |

## 2. Menú y productos

- Campos: nombre (único), categoría, precio (> 0), descripción, ícono o emoji, stock mínimo, activo, y temporada opcional: nombre y fecha "disponible hasta".
- **Disponible para la venta** = activo **y** no fuera de temporada (`hoy ≤ disponible hasta`) **y** con existencia vigente > 0.
- Motivo de no disponible, mostrado al mesero: *Retirado de la carta*, *Fuera de temporada*, *Agotado*.
- Filtros: texto, categoría y estado (En la carta, Agotados, De temporada, Fuera de temporada, Inactivos).

## 3. Pedidos y estados

### 3.1 Creación
- **Mesero**: solo desde una mesa cargada por QR. El mesero queda asignado automáticamente.
- **Cajero o Administrador**: eligen la mesa y el mesero (de la lista de meseros activos).
- Ítems con cantidad > 0, observación por ítem (máx. 80 caracteres) y observación general (máx. 140).
- Al crear: valida disponibilidad, consume lotes en orden **FEFO** dentro de la misma transacción, registra los movimientos y deja el pedido en `PENDIENTE`. Si un producto no alcanza → 422 indicando el producto y lo disponible; no se guarda nada.
- Una mesa puede tener varios pedidos abiertos; todos forman su **cuenta**.

### 3.2 Máquina de estados

```
PENDIENTE ──(Preparación: iniciar)──▶ EN_PREPARACION ──(Preparación: marcar listo)──▶ LISTO ──(Mesero: confirmar entrega)──▶ ENTREGADO
    │                                                                                                                     │
    └──(Admin/Cajero: cancelar, con motivo)──▶ CANCELADO              (Cajero: cobrar cuenta) ──▶ CERRADO ◀───────────────┘
```

- `CANCELADO` solo desde `PENDIENTE`; devuelve a sus lotes las cantidades consumidas (si el lote ya venció, la cantidad devuelta queda vencida).
- Cobrar la cuenta pasa a `CERRADO` todos los pedidos abiertos de la mesa (`PENDIENTE`, `EN_PREPARACION`, `LISTO`, `ENTREGADO`). La caja muestra cuántos no han sido entregados como advertencia, sin bloquear.
- Cualquier otra transición → 409.

### 3.3 Estado visible de la mesa
- **Disponible** (sin pedidos abiertos) u **Ocupada**.
- Si está ocupada, se muestra la etapa más urgente de sus pedidos: **Listo para recoger** > **En preparación** > **Pendiente** > **Servido** (todos entregados).
- El mesero también ve "Mis pedidos en curso" (sus pedidos no entregados, con su estado).

## 4. Preparación y avisos en tiempo real

- Tablero con pedidos `PENDIENTE`, `EN_PREPARACION` y `LISTO`, ordenados por estado y antigüedad, con mesa, mesero, ítems, observaciones y hora.
- **Marcar listo** pasa el pedido a `LISTO` y crea un aviso para **el mesero del pedido**. **Reenviar aviso** crea un aviso nuevo.
- El mesero recibe el aviso al instante (SSE): toast "Pedido #N listo · Mesa M", vibración en el celular, contador en la campana y la sección "Listos para recoger" con el botón **Recogido y entregado**.
- Preparación recibe al instante los pedidos nuevos (aviso y contador de pendientes).
- Todas las pantallas abiertas (mesas, caja, tablero) se actualizan solas ante cambios de pedidos.
- Los avisos se guardan: si el mesero estaba desconectado, al volver ve sus avisos no leídos. Confirmar la entrega marca como leídos los avisos de ese pedido.
- Limitación conocida: con la app cerrada no llega aviso (Web Push está fuera de alcance).

## 5. Mesas y QR

- Mesas con número único y estado activo. El administrador puede agregar o desactivar mesas.
- Cada mesa tiene un **QR** cuyo contenido es `LACAVA:MESA:<número>:<token>`, donde el token es un HMAC del número con `QR_SECRET`. Esto evita que alguien abra una mesa con un QR inventado.
- El administrador puede imprimir los QR (una hoja con todos, o uno por mesa) y regenerar el token de una mesa (invalida el QR anterior).
- Escaneo: `BarcodeDetector` si existe, si no `@zxing/browser`. Como respaldo se escribe el código manualmente; para el modo manual se acepta el **número de mesa más un PIN** de 4 dígitos que se genera junto con el token y se imprime bajo el QR.

## 6. Inventario por lotes

- La existencia se controla **por producto vendible** (no hay recetas ni insumos en el MVP).
- Cada entrada crea un **lote**: producto, cantidad, fecha de vencimiento (obligatoria, ≥ hoy), temporada opcional (por defecto la del producto) y fecha de recepción.
- **Existencia vigente** = suma de lotes no vencidos (`vence ≥ hoy`) con cantidad > 0.
- **Consumo FEFO**: al vender se descuenta primero del lote que vence antes.
- **Salidas manuales**: Merma, Producto dañado, Consumo interno o Ajuste, con cantidad ≤ vigente (consumo FEFO) y observación.
- **Baja de vencidos**: una acción deja en 0 todos los lotes vencidos con cantidad > 0, registrando un movimiento por lote.
- **Alertas**: agotados (activos y en temporada con vigente = 0), stock bajo (vigente ≤ mínimo), por vencer (próximo lote vence en ≤ 3 días) y con lotes vencidos.
- Vista de inventario con filtros (texto, categoría, estado, temporada), detalle de lotes por producto y movimientos recientes.

## 7. Caja y facturación

- Un solo turno abierto a la vez; base inicial ≥ 0; movimientos de entrada y salida con concepto.
- **Cobrar** requiere caja abierta. Cobra la cuenta completa de la mesa (suma de sus pedidos abiertos).
- Medios: **Efectivo** (recibido ≥ total; calcula vueltas), **Transferencia**, **Datáfono** (aprobación obligatoria).
- Cliente opcional: nombre, CC/NIT y correo.
- Se genera una **factura** con número `LC-<año>-<consecutivo de 5 dígitos>`, vinculada al turno, al cajero y a los pedidos cerrados, con una copia de nombre, precio y cantidad de cada ítem.
- Factura en **tirilla de 80 mm**: datos del negocio, número, fecha, mesa, mesero(s), cajero, cliente, ítems consolidados por producto (cantidad, precio unitario y valor), total, medio de pago, aprobación, recibido y vueltas. Leyenda de documento no electrónico.
- Tras cobrar se ofrece **Imprimir factura**; en Caja está la lista "Facturas emitidas hoy" con reimpresión.
- **Efectivo esperado** = base + facturas en efectivo **del turno** + entradas − salidas.
- Arqueo por denominaciones (las mismas de Proto) y cierre que guarda esperado, contado y diferencia.

## 8. Informes

- Selector de mes (y rango libre opcional).
- Indicadores: pedidos, consumo total, recaudado, saldo por cobrar.
- Ranking de **productos más vendidos** (unidades y valor), ventas por medio de pago.
- Detalle de cuentas con reimpresión de factura.
- Exportación a Excel (`.xlsx`) con resumen, ranking y detalle.
- Dashboard: ventas cobradas hoy, pedidos activos (y cuántos listos), mesas ocupadas, por cobrar; para el Administrador también el producto top del mes y las alertas de inventario.

## 9. Usuarios
- Crear (usuario único, nombre, rol, contraseña de mínimo 8 caracteres), activar o desactivar y restablecer contraseña.
- No se puede desactivar el último administrador activo.
- Un mesero desactivado no aparece para asignar pedidos; sus pedidos abiertos se conservan.

## 10. Reglas transversales
- Moneda COP sin decimales en pantalla. Todo cambio de dinero o existencias registra usuario y fecha.
- Nada se borra físicamente: productos, mesas y usuarios se desactivan; pedidos se cancelan.

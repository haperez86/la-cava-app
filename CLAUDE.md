# CLAUDE.md — La Cava (gestión de restaurante)

Sistema para un restaurante/bar de copas y tablas: mesas con QR, pedidos desde el celular del mesero, comandas de preparación con aviso en tiempo real, inventario por lotes con vencimiento, caja con factura en tirilla e informes. **Un solo negocio**: no hay multiempresa.

## Fuentes de verdad (en este orden)

1. `docs/05-decisiones.md`: decisiones tomadas. No las cambies; si crees que una debe cambiar, propónlo al final de ese archivo en "Propuestas" y continúa con la vigente.
2. `docs/01-especificacion-funcional.md`: reglas de negocio, roles, estados y flujos.
3. `docs/02-modelo-datos.md` y `docs/03-api.md`: modelo y contrato REST/SSE.
4. `docs/04-backlog.md`: historias de usuario (HU) con criterios de aceptación, en orden de implementación.
5. `demo/index.html`: **solo referencia visual y de flujo**. No portes su JavaScript: allí todo vive en el navegador (`localStorage`) y aquí la lógica, la validación y la sincronización viven en el backend.

Si algo no está definido, elige la opción más simple, regístrala en `docs/05-decisiones.md` y sigue.

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 21 (LTS), Spring Boot 4.1.x, Maven |
| Persistencia | PostgreSQL 16+ (instalado localmente, **sin Docker**), Spring Data JPA, Flyway |
| Seguridad | Spring Security, JWT (access token), BCrypt |
| Tiempo real | Server-Sent Events (`SseEmitter` de Spring MVC) |
| API | REST JSON, errores `ProblemDetail` (RFC 9457), springdoc-openapi |
| Reportes | Apache POI (exportación `.xlsx`) |
| Frontend | Angular 22.x, componentes standalone, signals, Signal Forms (D18), Angular CDK, SCSS propio |
| Pruebas | JUnit 5, Mockito, Spring Boot Test + MockMvc contra PostgreSQL local de pruebas; Vitest en el frontend |

Permitidas además: MapStruct, `@zxing/browser` (lectura de QR cuando el navegador no trae `BarcodeDetector`), una librería para **generar** QR en el frontend (p. ej. `qrcode`). Cualquier otra se justifica en `docs/05-decisiones.md`.

## Estructura del repositorio

```
/backend    Spring Boot (paquete base co.lacava.gestion)
/frontend   Angular
/docs       Especificación, modelo, API, backlog y decisiones
/demo       Demo HTML de referencia (no se despliega)
```

### Backend: paquetes por funcionalidad

```
co.lacava.gestion
 ├─ shared/        config, seguridad, errores, dinero/fechas, reloj inyectable (Clock)
 ├─ auth/
 ├─ users/
 ├─ menu/          productos, categorías, temporadas
 ├─ inventory/     lotes, consumo FEFO, movimientos, bajas por vencimiento
 ├─ tables/        mesas y tokens QR
 ├─ orders/        pedidos, ítems, máquina de estados
 ├─ kitchen/       tablero de preparación
 ├─ notifications/ SSE y avisos persistidos
 ├─ cash/          turnos, movimientos, arqueo
 ├─ billing/       cobro de cuentas y facturas
 └─ reports/
```

Dentro de cada paquete: `api`, `application`, `domain`, `infrastructure`. Los controllers nunca exponen entidades JPA.

### Frontend

```
src/app
 ├─ core/       auth, guards por rol, layout, cliente SSE, toasts, impresión
 ├─ shared/     componentes UI, pipes (COP, fechas), escáner QR
 └─ features/   dashboard, tables, orders, kitchen, menu, inventory, cash, billing, reports, users
```

## Comandos

```bash
# Backend (desde /backend)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # puerto 8081 (D19)
./mvnw verify

# Frontend (desde /frontend)
npm install
npm start          # http://localhost:4201, proxy /api → http://localhost:8081 (incluye SSE)
npm test
npm run build
```

```sql
CREATE DATABASE la_cava;
CREATE DATABASE la_cava_test;
```

Credenciales por variables de entorno (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `QR_SECRET`); deja `backend/.env.example` y nunca subas secretos.

Puertos: **8081** (backend) y **4201** (frontend), porque 8080/4200 son de Proto Inventario. Para las instancias de verificación del agente usa **8091** y **4291** (D19).

## Convenciones obligatorias

**Backend**
- Dinero: `BigDecimal` / `NUMERIC(14,2)`. Fechas: `timestamptz` e `Instant`; fechas de vencimiento como `DATE` / `LocalDate`. Zona de negocio: `America/Bogota`.
- Usa un `Clock` inyectado para todo cálculo de "hoy" (vencimientos, temporadas, informes) y así poder probarlo.
- Migraciones Flyway `V{n}__descripcion.sql`; nunca edites una ya aplicada.
- Tablas y columnas en `snake_case`; código en inglés; textos al usuario en español.
- **Existencias**: solo `InventoryService` crea, consume o da de baja lotes, y siempre registra el movimiento en la misma transacción. El consumo es **FEFO** (primero el lote que vence antes) y guarda qué lotes consumió cada ítem (`order_item_lot`) para poder devolverlos si se cancela el pedido.
- **Estados del pedido**: transiciones solo a través de `OrderStateMachine` (ver especificación §3). Una transición inválida → 409.
- **Notificaciones**: se publican como eventos de dominio (`ApplicationEventPublisher`) **después del commit** (`@TransactionalEventListener(phase = AFTER_COMMIT)`) y además se persisten en `notification`, para que un mesero que se reconecta las reciba.
- Autorización con `@PreAuthorize` por rol en cada endpoint; además el mesero solo opera pedidos propios.
- Concurrencia: `@Version` en `stock_lot`, `customer_order` y `bill`; números de factura desde una secuencia de PostgreSQL.

**Frontend**
- **Mobile-first**: el perfil Mesero se usa en celular (390 px) y Preparación en tablet o PC.
- Estilo visual: usa el skill `lacava-design` (colores exactos de la demo, tipografía, componentes y tirilla). Tokens en `styles/_tokens.scss`; nada de hex sueltos en los componentes.
- Para código Angular usa el skill `angular-developer` (en especial `references/signal-forms.md`) y el MCP `angular-cli` de `.mcp.json`.
- Estado con signals; servicios por funcionalidad; el cliente SSE en `core/` con reconexión automática y refresco de datos al reconectar.
- Avisos con toast (y vibración en el celular del mesero), nunca `alert()`.
- Impresión de comanda y factura en tirilla de 80 mm con iframe oculto (ver `printHTML` en la demo).

## Orquestación (sesión principal = Opus)

La sesión principal es el **orquestador** y no escribe código de producción. Delega en los subagentes de `.claude/agents/`:

| Subagente | Modelo | Rol |
|---|---|---|
| `developer` | Sonnet | Implementa la HU (código + pruebas). Único que edita `backend/` y `frontend/`. |
| `test-runner` | Haiku | Corre `./mvnw verify`, `npm test`, `npm run build` y reporta. No edita nada. |
| `code-explorer` | Haiku | Localiza código y patrones (también en `../proto-inventario-app`). Solo lectura. |
| `hu-reviewer` | Opus | Revisa el diff contra los CA y las convenciones antes del commit. Solo lectura. |

El orquestador sí puede editar `docs/` (backlog, decisiones, API) y hacer los commits.

## Flujo de trabajo por HU

1. **Orquestador**: lee la HU y las secciones de docs que referencia; si hace falta, pide a `code-explorer` qué existe ya. Prepara para `developer` un encargo con: HU, CA, secciones a leer, archivos probables y restricciones.
2. **developer** implementa (migración → backend con pruebas → frontend → pruebas del frontend). HUs grandes se parten en encargos backend y frontend.
3. **test-runner** ejecuta todo. Si falla → vuelve a `developer` con el reporte. Máximo **3 ciclos**; luego el orquestador marca la HU `⛔ Bloqueada`, documenta el motivo y sigue con otra que no dependa de ella.
4. **hu-reviewer** revisa. Con hallazgos bloqueantes → vuelve a `developer` y luego a `test-runner`.
5. **Orquestador**: marca la HU `✅ Hecha` en el backlog, anota desviaciones y hace **un commit por HU**: `feat(LCV-HU-12): aviso al mesero cuando el pedido está listo`.

Una rama por épica (`feat/lcv-e0-fundaciones`, `feat/lcv-e1-menu`, …). Al terminar la épica, el orquestador resume lo hecho y se detiene para que el dueño revise el PR; no fusiona ni hace push forzado.

No avances con pruebas fallando ni marques una HU como hecha sin el veredicto APROBADO de `hu-reviewer`.

## Reutilización desde Proto Inventario

Si existe `../proto-inventario-app` (mismo stack), **copia y adapta** en lugar de escribir desde cero. No se crea librería compartida: se copia el código y se registra en `docs/05-decisiones.md` de qué commit de Proto se partió.

- **Copiar y adaptar**: esqueleto Maven/Angular, config de Flyway y perfiles, seguridad JWT y BCrypt, manejo de `ProblemDetail`, usuarios y roles, guards e interceptor, layout responsive, toasts, tablas paginadas, pipe COP, impresión en tirilla de 80 mm, importación CSV, turno de caja con arqueo y conciliación de datáfono/transferencias, exportación a Excel.
- **Adaptar con cuidado**: kardex de Proto → aquí los movimientos van **por lote** con FEFO y `order_item_lot`; anulaciones → aquí solo se cancela en `PENDING` (D11); caja → cobro por cuenta de mesa (D14).
- **No traer**: precio negociado del POS, lector de código de barras, nada del dominio de balones, ni paquetes `co.proto…` (renombrar a `co.lacava.gestion`).

## Definition of Done

- Criterios cumplidos y probados; sin errores de compilación ni de consola.
- OpenAPI alineado con `docs/03-api.md` (actualiza el documento si cambia).
- Pantallas del mesero usables a 390 px; tablero de preparación usable en tablet.
- Datos de ejemplo del perfil `local` actualizados.

## Fuera de alcance (no implementar)

Docker, multiempresa, microservicios, factura electrónica DIAN, pasarela de pagos, notificaciones push con la app cerrada (Web Push), recetas/insumos con descuento por receta, reservas, propinas, división de cuentas.

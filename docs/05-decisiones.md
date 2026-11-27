# 05 · Decisiones — La Cava

| # | Decisión | Motivo |
|---|---|---|
| D1 | Monorepo con `/backend` y `/frontend`. | Un solo contexto para el agente y cambios que cruzan capas en un solo commit. |
| D2 | Un solo negocio, sin multiempresa. | Alcance del MVP. |
| D3 | Sin Docker: PostgreSQL local, también para las pruebas de integración (`la_cava_test`). No se usa Testcontainers. | Decisión del dueño del producto. |
| D4 | Java 21 LTS, Spring Boot 4.1.x, Angular 22.x. | Versiones estables vigentes. |
| D5 | Monolito modular con paquetes por funcionalidad. | Tamaño del dominio. |
| D6 | JWT HS256 de 10 horas (un turno), sin refresh token en el MVP. | Simplicidad. |
| D7 | Tiempo real con **SSE** (no WebSocket). La autenticación del stream usa un ticket de un solo uso y 60 s, porque `EventSource` no envía headers. | Los avisos van del servidor al cliente; SSE es más simple, pasa por proxies HTTP y reconecta solo. |
| D8 | Eventos publicados **después del commit** y avisos persistidos en `notification`. | No avisar de algo que se revirtió y no perder avisos si el mesero estaba desconectado. |
| D9 | Los emisores SSE viven en memoria de **una sola instancia** del backend. | Suficiente para un restaurante. Para escalar a varias instancias se usaría `LISTEN/NOTIFY` de PostgreSQL; queda como mejora futura. |
| D10 | Inventario por **producto vendible con lotes**, sin recetas ni insumos. Consumo FEFO al **crear** el pedido (no al cobrar). | Refleja la demo; reservar al pedir evita vender lo que ya no hay. |
| D11 | Cancelar un pedido solo en `PENDING`; devuelve las cantidades al lote exacto (`order_item_lot`). | Lo que ya se preparó no vuelve al inventario. |
| D12 | El QR contiene `LACAVA:MESA:<n>:<token HMAC>`; respaldo manual con número de mesa y PIN de 4 dígitos generado junto con el token. | Evita abrir mesas con códigos inventados o adivinados. |
| D13 | El mesero **no** puede abrir mesas sin el QR, ni siquiera desde la API (`tableCode` obligatorio para su rol). | Requisito del negocio: el pedido debe quedar en la mesa correcta. |
| D14 | Cobro por cuenta completa de la mesa; sin división de cuentas ni propinas en el MVP. | Alcance. |
| D15 | Factura generada como tirilla de 80 mm en el frontend; no es factura electrónica DIAN. | DIAN está fuera de alcance. |
| D16 | Cálculos de fecha con un `Clock` inyectado y zona `America/Bogota`. | Vencimientos y temporadas probables con pruebas deterministas. |
| D17 | UI con SCSS propio y Angular CDK, sin Angular Material. | Mantener el estilo de la demo. |
| D18 | Formularios con **Signal Forms** de Angular 22 (no Reactive Forms). | Mismo enfoque que Proto Inventario (su D43), para poder reutilizar componentes. |
| D19 | Puertos locales 8081 (backend) y 4201 (frontend); verificación del agente en 8091/4291. En el VPS el backend también usa 8081. | 8080/4200 son de Proto Inventario, y así coincide con el plan del VPS. |
| D20 | Sistema visual definido en el skill `.claude/skills/lacava-design`, con los colores exactos de `demo/index.html`. | Que todo el frontend use una sola paleta y no se inventen colores. |
| D21 | Skills `angular-developer` y `angular-new-app` (angular/skills, commit fijado en `skills-lock.json`) y el MCP `angular-cli`, copiados de Proto. | Mismo soporte de Angular que en Proto. |
| D22 | El repositorio git se inició localmente con `main` (commit inicial con specs y demo). Se ignoran todas las carpetas que empiezan con punto (`.*/`) salvo `.mvn/`; `.claude/` y `.atl/` no se versionan. | Pedido del dueño. `.mvn/` es necesaria para que `./mvnw` funcione en otro clon. Consecuencia: agentes y skills (D20, D21) viven solo en la máquina local. |
| D23 | Proxy de desarrollo `/api` → 8081 con `timeout: 0`, `proxyTimeout: 0` y `Accept-Encoding: identity` para no cortar ni comprimir `text/event-stream`. | Requisito de SSE de HU-00; se valida con un stream real en la épica 5. |
| D24 | JWT con `spring-boot-starter-security-oauth2-resource-server` (Nimbus, HS256, emisor `la-cava`) en lugar de jjwt; el rol se lee de la BD en cada petición y un usuario inactivo queda fuera aunque su token siga vigente. | Mismo enfoque que Proto y sin librería extra. |
| D25 | Rutas por rol: `/dashboard` (A, C), `/mesas` (A, C, M; "Mis mesas" para M), `/preparacion` (A, P; "Comandas"). Las rutas ajenas o desconocidas llevan a la pantalla inicial del rol. | CA1 y CA3 de HU-01; el menú crece con cada HU. |
| D26 | Sesión (token y usuario) en `localStorage` con la clave `lacava.session`; un 401 cierra la sesión y un 403 solo muestra un toast. Sin selector de rol como en la demo. | Simplicidad; el backend es quien aplica los permisos. |
| D27 | Login con límite de intentos: tras 5 fallos seguidos por usuario (exista o no), bloqueo de 15 min en memoria (una instancia, D9); mientras dure, el mismo 401 "Usuario o contraseña incorrectos". Un login exitoso reinicia el contador. | Propuesta P1 aprobada por el dueño tras la revisión de seguridad de HU-01. |
| D28 | `LocalDataSeeder` solo siembra si, además del perfil `local`, la URL de la base apunta a `localhost`, `127.0.0.1` o `::1`; si no, registra un aviso y no crea datos. | Propuesta P2 aprobada: las claves `<usuario>123` de docs/02 no deben llegar a una base compartida. |

## Propuestas
_(El agente registra aquí los cambios que sugiera, con su motivo. No se aplican hasta que se aprueben.)_

| # | Propuesta | Motivo |
|---|---|---|
| P3 | Revisar D26 antes de producción: token en cookie `httpOnly` o vigencia menor que 10 h (D6). | Revisión de HU-01: un XSS futuro podría leer el token de `localStorage`. Mitigado hoy porque el backend comprueba en cada petición que el usuario siga activo. |


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

## Propuestas
_(El agente registra aquí los cambios que sugiera, con su motivo. No se aplican hasta que se aprueben.)_

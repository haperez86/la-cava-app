# La Cava — Gestión de restaurante

Mesas con QR, pedidos desde el celular del mesero, comandas con aviso en tiempo real, inventario por lotes con vencimiento, caja con factura en tirilla e informes.

- **Backend:** Java 21 · Spring Boot 4.1 · PostgreSQL · Flyway · SSE
- **Frontend:** Angular 22
- **Demo de referencia (HTML):** [`demo/index.html`](demo/index.html), publicada en https://haperez86.github.io/La-cava/

## Estado

Paquete de arranque: la especificación y el backlog están listos y el código se construye por historias de usuario.

| Documento | Contenido |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | Instrucciones, stack y convenciones para el agente de desarrollo |
| [`docs/01-especificacion-funcional.md`](docs/01-especificacion-funcional.md) | Roles, estados del pedido, reglas de negocio |
| [`docs/02-modelo-datos.md`](docs/02-modelo-datos.md) | Tablas, restricciones y datos de ejemplo |
| [`docs/03-api.md`](docs/03-api.md) | Contrato REST y SSE |
| [`docs/04-backlog.md`](docs/04-backlog.md) | Historias de usuario con criterios de aceptación |
| [`docs/05-decisiones.md`](docs/05-decisiones.md) | Decisiones de arquitectura y alcance |

## Requisitos locales

- JDK 21, Node.js 22 LTS o superior, PostgreSQL 16 o superior (sin Docker).
- Crear las bases de datos `la_cava` y `la_cava_test`.

## Cómo empezar con Claude Code

```bash
git clone https://github.com/haperez86/<este-repo>.git && cd <este-repo>
claude
```

La sesión principal corre en Opus (`.claude/settings.json`) y delega en subagentes: `developer` (Sonnet), `test-runner` y `code-explorer` (Haiku), `hu-reviewer` (Opus). Verifica con `/model` y `/agents` al abrir.

Para reutilizar Proto, clona `proto-inventario-app` al lado de este repo (`../proto-inventario-app`).

Primer mensaje sugerido:

> Lee CLAUDE.md y toda la carpeta docs/. Eres el orquestador: no escribas código de producción tú mismo. Crea la rama `feat/lcv-e0-fundaciones` e implementa LCV-HU-00 y LCV-HU-01 con el flujo de orquestación (code-explorer → developer → test-runner → hu-reviewer → commit), reutilizando de `../proto-inventario-app` lo indicado en "Reutilización desde Proto". Registra en docs/05-decisiones.md el commit de Proto del que partiste. Al terminar la épica, resume y detente.

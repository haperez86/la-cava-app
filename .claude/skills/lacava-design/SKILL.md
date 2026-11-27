---
name: lacava-design
description: Sistema visual de La Cava tomado de demo/index.html (colores, tipografía, tarjetas, botones, pills de estado, navegación, toasts y tirilla de 80 mm). Úsalo siempre que crees o modifiques estilos, componentes visuales o pantallas del frontend de La Cava.
---

# La Cava · Sistema de diseño

Fuente: `demo/index.html`. Si algo no está aquí, mira la demo y copia su estilo; **no inventes colores nuevos**. SCSS propio + Angular CDK, sin Angular Material (D17).

## Tokens (`frontend/src/styles/_tokens.scss`)

Usa estos valores exactos como CSS custom properties en `:root`. En los componentes usa siempre `var(--…)`, nunca un hex suelto.

```scss
:root {
  /* Marca (exactos de la demo) */
  --green:   #10271f;  /* sidebar, botón primario */
  --green2:  #19392e;  /* verde secundario / hover sobre verde */
  --gold:    #c6a16a;  /* botón dorado, acentos */
  --cream:   #f7f4ed;
  --ink:     #202821;  /* texto principal */
  --muted:   #778078;  /* texto secundario */
  --line:    #e6e8e2;  /* bordes */
  --white:   #fff;
  --red:     #b84d43;  /* error, vencido, agotado, faltante */
  --amber:   #c28a35;  /* advertencia, stock bajo, por vencer, borde de comanda */
  --shadow:  0 8px 28px #14251a12;

  /* Superficies */
  --bg:            #f3f5f1;  /* fondo de la app (body) */
  --surface:       #fff;     /* tarjetas */
  --sidebar-text:  #f8f5ed;
  --nav-text:      #dce4dc;
  --nav-active-bg: #ffffff16;
  --nav-active:    #f1d39f;
  --brand-title:   #e4c38e;  /* "LA CAVA" en el sidebar */

  /* Estados (pill: fondo / texto) */
  --ok-bg:     #edf2ed;  --ok:     #3f7454;  /* Disponible, cuadra, servido */
  --busy-bg:   #fff1d9;  --busy:   #96651e;  /* Pendiente, en preparación, sobrante */
  --ready-bg:  #e3eefb;  --ready:  #2f5f8f;  /* Listo para recoger (también toast "listo") */
  --danger-bg: #fbe9e6;  --danger: #a4473d;  /* Cancelado, vencido, error */
  --season:    #7a5a2c;  --season-bg: #f1eadb; /* temporada, ranking */

  /* Forma */
  --radius-card: 14px;
  --radius-btn:  9px;
  --radius-nav:  10px;
  --radius-pill: 99px;

  /* Tipografía */
  --font-body:  Inter, system-ui, -apple-system, "Segoe UI", sans-serif;
  --font-title: Georgia, serif;
  --font-ticket: "Courier New", monospace;
}
```

`Inter` no se carga desde la red en la demo: queda como preferencia y cae a la fuente del sistema. No agregues Google Fonts sin registrarlo en decisiones.

## Base

- `body`: `background: var(--bg)`, `color: var(--ink)`, `font: 15px/1.45 var(--font-body)`. Inputs, botones y selects heredan la fuente.
- Títulos de marca y de sección: `var(--font-title)`, peso 600; el logo "LA CAVA" con `letter-spacing: 3px` y `var(--brand-title)`.

## Componentes

| Componente | Estilo |
|---|---|
| Tarjeta `.card` | fondo blanco, `1px solid var(--line)`, radio 14px, padding 18px (16px en móvil), `var(--shadow)` |
| Botón `.btn` | borde `var(--line)`, fondo blanco, `padding 10px 14px`, radio 9px, peso 600, texto `var(--ink)` |
| Botón primario | fondo y borde `var(--green)`, texto blanco |
| Botón dorado | fondo y borde `var(--gold)`, texto `#17251d` |
| Botón pequeño | `padding 7px 10px`, 12px |
| Pill de estado | `inline-flex`, radio 99px, `padding 4px 8px`, 10px, peso 700; colores según los tokens de estado |
| Comanda (tablero de preparación) | tarjeta con `border-left: 4px solid var(--amber)` |
| Campana de avisos | contador rojo (`var(--red)`, texto blanco, 18px de alto) arriba a la derecha |
| Toast | toast "listo" con fondo `var(--ready)` y texto blanco; nunca `alert()` |
| Pedido listo | animación `glow` con sombra `#2f5f8f44 → #2f5f8f00` |
| Texto de alerta | `.txt-red` = `var(--red)`, `.txt-amber` = `var(--busy)` |
| Arqueo | diferencia 0 → `var(--ok)`; faltante → `var(--red)`; sobrante → `var(--busy)` |

## Responsive (mobile-first)

Escribe primero el estilo de celular y agrega `min-width` hacia arriba. Breakpoints tomados de la demo, como variables SCSS en `_breakpoints.scss`:

| Nombre | Rango | Uso principal |
|---|---|---|
| `xs` | ≤ 380px | celulares pequeños |
| `mobile` | ≤ 700px | **mesero** (diseño de referencia: 390px) |
| `tablet` | 701–1100px | **preparación** en tablet |
| `desktop` | ≥ 1051px | administrador y cajero |

**Layout**
- ≥ 701px: sidebar fijo `var(--green)` (texto `var(--sidebar-text)`, `padding 24px 16px`); navegación `var(--nav-text)`, radio 10px; la opción activa o en hover usa fondo `var(--nav-active-bg)` y texto `var(--nav-active)`. Contenido con `padding 30px 34px`.
- ≤ 700px: sin sidebar; barra **`.mobile-nav` fija abajo** (fondo blanco, `border-top: 1px solid var(--line)`, `padding-bottom: calc(7px + env(safe-area-inset-bottom))`) y `.app` con `padding-bottom: 76px` para que no tape el contenido. Topbar de 60px sin migas de pan; contenido con `padding 18px 12px` (10px laterales si ≤ 380px).
- Nada de scroll horizontal: `overflow-x: hidden` en `.app`, `.main` y `.content`; `min-width: 0` y `overflow-wrap: anywhere` en tarjetas y títulos.

**Rejillas**
| Rejilla | Escritorio | ≤ 1050px | ≤ 700px | ≤ 380px |
|---|---|---|---|---|
| Indicadores (`.stats`) | 4 col | 2 | 1 | 2 compactas |
| Mesas (`.table-grid`) | 4+ | 3 | 2 | 1 |
| Menú (`.menu-grid`) | 3+ | 2 | 2 | 2 |
| Comandas (`.kitchen-grid`) | 3 (min 280px) | 2 | 1 | 1 |
| Dos columnas (`.layout2`, `.form2`) | 2 | 1 | 1 | 1 |

**Controles táctiles (≤ 700px)**
- Botones de al menos **42px** de alto; inputs y selects de al menos **44px**; botón "Escanear QR" de 56px y 16px.
- Inputs con `font-size: 16px` en móvil (la demo usa 15px, y en iPhone eso provoca zoom al enfocar).
- Encabezado de página en columna con el botón principal al 100%; toolbar en 2 columnas (1 si ≤ 380px), con el buscador ocupando toda la fila.
- Acciones de modal: botones con `flex: 1`; en formularios largos, pie fijo (`.sticky-foot`) con la acción principal (proporción 1fr / 2fr) respetando `safe-area-inset-bottom`.
- Toasts abajo (`bottom: 86px`, por encima de la barra), a todo el ancho con 12px de margen.
- Tablas de datos: en móvil se muestran como tarjetas o con scroll horizontal **dentro** de su contenedor, nunca de la página.

**Preparación (≥ 701px)**: tarjetas de comanda con `padding 24px`, título de 23px, filas de 54px de alto y acciones en 2 columnas con botones de 50px.

Verificación obligatoria de cada pantalla: 390px, 768px y escritorio.

## Tirilla de 80 mm (comanda y factura)

- Ancho `72mm`, centrado, `font: 12px/1.4 var(--font-ticket)`, texto negro sobre blanco.
- Se imprime con un iframe oculto (ver `printHTML` en la demo), sin ventanas emergentes.

## Reglas

1. Mismos colores en ambos temas: no hay modo oscuro en el MVP.
2. Contraste: el texto sobre `--gold` va en `#17251d`, nunca en blanco.
3. El color nunca es la única señal de un estado: la pill siempre lleva texto ("Listo", "Agotado").
4. Antes de dar por terminada una pantalla, revísala a 390px, 768px y en escritorio.

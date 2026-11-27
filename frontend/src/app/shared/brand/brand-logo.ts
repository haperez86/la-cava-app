import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Logo de La Cava: círculo verde con copa, botella de pie y botella acostada en trazo dorado,
 * y el texto "LA CAVA," / "COPAS Y TABLAS" separado por una línea con un rombo.
 * SVG inline vectorial; el dorado llega por `currentColor` y el tamaño (px) por `size`.
 */
@Component({
  selector: 'app-brand-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    :host {
      display: inline-flex;
      flex: none;
      color: var(--gold);
    }

    /* Se encoge si el contenedor limita el ancho del host (p. ej. login en ≤380px). */
    svg {
      display: block;
      max-width: 100%;
      height: auto;
    }

    .disc {
      fill: var(--green);
      stroke: currentColor;
      stroke-width: 1.5;
    }

    .art {
      fill: none;
      stroke: currentColor;
      stroke-width: 2;
      stroke-linecap: round;
      stroke-linejoin: round;
    }

    .wine {
      stroke-width: 1.5;
    }

    text {
      fill: currentColor;
      font-family: var(--font-title);
      text-anchor: middle;
    }

    .name {
      font-size: 18px;
      font-weight: 600;
      letter-spacing: 3px;
    }

    .tagline {
      font-size: 8.5px;
      letter-spacing: 2.2px;
    }

    .divider {
      fill: none;
      stroke: currentColor;
      stroke-width: 1;
    }

    .diamond {
      fill: currentColor;
    }
  `,
  template: `
    <svg
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 200 200"
      role="img"
      aria-label="La Cava, copas y tablas"
      focusable="false"
    >
      <circle class="disc" cx="100" cy="100" r="98" />
      <g class="art">
        <!-- botella de pie, detrás a la derecha -->
        <path d="M108 34h8v18c0 6 8 9 8 18v54h-24V70c0-9 8-12 8-18z" />
        <path d="M104 84h16v16h-16z" />
        <!-- copa con el nivel del vino -->
        <path d="M60 52h32c0 26-6 40-16 42c-10-2-16-16-16-42z" />
        <path class="wine" d="M61.5 68h29" />
        <path d="M76 94v28" />
        <path d="M65 124c6-3 16-3 22 0" />
        <!-- botella acostada en la base, a la derecha -->
        <path d="M128 108h24c6 0 8 5 12 5h6v6h-6c-4 0-6 5-12 5h-24z" />
        <path d="M52 125h116" />
      </g>
      <text class="name" x="101.5" y="150">LA CAVA,</text>
      <path class="divider" d="M62 158h32M106 158h32" />
      <path class="diamond" d="M100 154l4 4-4 4-4-4z" />
      <text class="tagline" x="101" y="174">COPAS Y TABLAS</text>
    </svg>
  `,
})
export class BrandLogo {
  /** Ancho y alto del logo en px. */
  readonly size = input(160);
}

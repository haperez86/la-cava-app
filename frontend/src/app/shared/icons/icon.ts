import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type IconName = 'home' | 'tables' | 'kitchen' | 'menu' | 'calendar';

/** Trazos (viewBox 24x24) de cada ícono; SVG inline para no depender de glifos Unicode ni de librerías. */
export const ICON_PATHS: Readonly<Record<IconName, readonly string[]>> = {
  home: ['M3 11l9-8 9 8', 'M5 10v10h14V10', 'M10 20v-6h4v6'],
  tables: ['M3 3h7v7H3z', 'M14 3h7v7h-7z', 'M3 14h7v7H3z', 'M14 14h7v7h-7z'],
  kitchen: ['M6 3v7a3 3 0 0 0 3 3v8', 'M9 3v7', 'M3 3v7a3 3 0 0 0 3 3', 'M18 3c-2 2-3 5-3 8h3v10'],
  menu: ['M4 6h16', 'M4 12h16', 'M4 18h16'],
  calendar: ['M4 5h16v16H4z', 'M4 10h16', 'M8 3v4', 'M16 3v4'],
};

/** Ícono SVG decorativo; hereda el color con `currentColor` y el tamaño con `size`. */
@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true', style: 'display: inline-flex; flex: none' },
  template: `
    <svg
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      @for (d of paths(); track d) {
        <path [attr.d]="d" />
      }
    </svg>
  `,
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly size = input(18);
  protected readonly paths = computed(() => ICON_PATHS[this.name()]);
}

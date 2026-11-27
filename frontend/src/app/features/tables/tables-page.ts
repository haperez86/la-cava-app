import { Component, computed, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { TablesAdmin } from './tables-admin';

/**
 * `/mesas` (D25). ADMIN gestiona mesas y QR (LCV-HU-07); para cajero y mesero sigue el aviso
 * hasta que lleguen "Mis mesas" (HU-08) y el estado de las mesas (HU-09).
 */
@Component({
  selector: 'app-tables-page',
  imports: [TablesAdmin],
  template: `
    @if (isAdmin()) {
      <app-tables-admin />
    } @else {
      <h2>Mesas</h2>
      <p class="muted" data-testid="tables-placeholder">Esta pantalla estará disponible próximamente.</p>
    }
  `,
})
export class TablesPage {
  readonly #auth = inject(AuthService);
  protected readonly isAdmin = computed(() => this.#auth.role() === 'ADMIN');
}

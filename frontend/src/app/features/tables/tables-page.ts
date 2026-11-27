import { Component, computed, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { TablesAdmin } from './tables-admin';
import { WaiterTables } from './waiter-tables';

/**
 * `/mesas` (D25). ADMIN gestiona mesas y QR (LCV-HU-07); el mesero ve "Mis mesas" y las abre por QR (LCV-HU-08);
 * el cajero sigue con el aviso hasta el estado de las mesas (HU-09).
 */
@Component({
  selector: 'app-tables-page',
  imports: [TablesAdmin, WaiterTables],
  template: `
    @switch (role()) {
      @case ('ADMIN') {
        <app-tables-admin />
      }
      @case ('WAITER') {
        <app-waiter-tables />
      }
      @default {
        <h2>Mesas</h2>
        <p class="muted" data-testid="tables-placeholder">Esta pantalla estará disponible próximamente.</p>
      }
    }
  `,
})
export class TablesPage {
  readonly #auth = inject(AuthService);
  protected readonly role = computed(() => this.#auth.role());
}

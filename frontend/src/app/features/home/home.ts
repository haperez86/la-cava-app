import { Component, inject } from '@angular/core';
import { HealthService } from '../../core/http/health.service';
import { ToastService } from '../../core/notifications/toast.service';

/** Pantalla placeholder de HU-00: muestra el estado de /health hasta que exista el Dashboard. */
@Component({
  selector: 'app-home',
  template: `
    <h2>Bienvenido a La Cava</h2>
    <section class="card">
      <p class="muted">Estado del servicio</p>
      <p data-testid="home-health">
        @switch (health.status()) {
          @case ('up') {
            <span class="pill">API y base de datos operativas</span>
          }
          @case ('down') {
            <span class="pill danger">Sin conexión con el servidor</span>
          }
          @default {
            <span class="pill busy">Verificando…</span>
          }
        }
      </p>
      <button type="button" class="btn primary" (click)="recheck()">Reintentar</button>
    </section>
  `,
})
export class Home {
  protected readonly health = inject(HealthService);
  readonly #toast = inject(ToastService);

  protected recheck(): void {
    this.health.check();
    this.#toast.info('Verificando el servicio…');
  }
}

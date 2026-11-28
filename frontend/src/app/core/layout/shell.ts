import { Component, DestroyRef, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ROLE_LABELS } from '../auth/role';
import { HealthService } from '../http/health.service';
import { NotificationService } from '../notifications/notification.service';
import { SseClient } from '../notifications/sse-client';
import { BrandLogo } from '../../shared/brand/brand-logo';
import { Icon } from '../../shared/icons/icon';
import { NavItem, navItemsFor, navLabel } from './nav-items';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, BrandLogo],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  readonly #router = inject(Router);
  protected readonly health = inject(HealthService);
  readonly #auth = inject(AuthService);
  protected readonly user = this.#auth.user;
  protected readonly roleLabel = computed(() => ROLE_LABELS[this.#auth.role() ?? 'ADMIN']);
  protected readonly initials = computed(() =>
    (this.user()?.fullName ?? '')
      .split(/\s+/)
      .filter((w) => w.length > 0)
      .slice(0, 2)
      .map((w) => w[0].toUpperCase())
      .join(''),
  );
  /** HU-14: campana con los avisos no leídos (mesero, D42, y Preparación, D43). */
  protected readonly isWaiter = computed(() => this.#auth.role() === 'WAITER');
  /** HU-15 (D43): Preparación también ve la campana; lleva al tablero. */
  protected readonly hasBell = computed(() => this.#auth.role() === 'WAITER' || this.#auth.role() === 'KITCHEN');
  protected readonly bellLink = computed(() => (this.isWaiter() ? '/mesas' : '/preparacion'));
  protected readonly bellFragment = computed(() => (this.isWaiter() ? 'listos' : undefined));
  protected readonly unreadCount = inject(NotificationService).count;
  protected readonly bellLabel = computed(() => {
    const n = this.unreadCount();
    return n === 0 ? 'Avisos: ninguno sin leer' : `Avisos: ${n} sin leer`;
  });
  protected readonly items = computed(() =>
    navItemsFor(this.#auth.role()).map((item) => ({ ...item, label: navLabel(item, this.#auth.role()) })),
  );

  readonly #url = toSignal(
    this.#router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.#router.url },
  );

  protected readonly title = computed(() => {
    const segment = this.#url().split(/[/?#]/).find((s) => s.length > 0) ?? '';
    return this.items().find((i: NavItem) => i.path === segment)?.label ?? 'La Cava';
  });

  constructor() {
    this.health.check();
    // HU-13: el canal SSE vive mientras el usuario está dentro de la aplicación.
    const sse = inject(SseClient);
    sse.start();
    inject(DestroyRef).onDestroy(() => sse.stop());
  }

  protected logout(): void {
    this.#auth.logout();
    void this.#router.navigateByUrl('/login');
  }
}

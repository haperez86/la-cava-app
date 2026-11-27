import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ROLE_LABELS } from '../auth/role';
import { HealthService } from '../http/health.service';
import { NavItem, navItemsFor, navLabel } from './nav-items';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
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
  }

  protected logout(): void {
    this.#auth.logout();
    void this.#router.navigateByUrl('/login');
  }
}

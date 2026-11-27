import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { HealthService } from '../http/health.service';
import { NAV_ITEMS } from './nav-items';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  readonly #router = inject(Router);
  protected readonly health = inject(HealthService);
  protected readonly items = NAV_ITEMS;

  readonly #url = toSignal(
    this.#router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.#router.url },
  );

  protected readonly title = computed(() => {
    const segment = this.#url().split(/[/?#]/).find((s) => s.length > 0) ?? '';
    return NAV_ITEMS.find((i) => i.path === segment)?.title ?? 'La Cava';
  });

  constructor() {
    this.health.check();
  }
}

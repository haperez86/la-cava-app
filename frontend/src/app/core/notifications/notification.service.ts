import { HttpClient } from '@angular/common/http';
import { Service, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import { AppNotification } from './notification.model';
import { SseClient } from './sse-client';
import { ToastService } from './toast.service';

export const UNREAD_URL = '/api/v1/notifications/unread';
export const VIBRATION_PATTERN_MS: readonly number[] = [200, 100, 200];

/**
 * Avisos del usuario (LCV-HU-14, D42): el contador de la campana sale de `unread`. Un aviso que llega por SSE suma al
 * contador, muestra el toast y vibra; al conectar y al reconectar el canal se vuelve a consultar `unread` (CA2).
 */
@Service()
export class NotificationService {
  readonly #http = inject(HttpClient);
  readonly #sse = inject(SseClient);
  readonly #toast = inject(ToastService);

  readonly #unread = signal<readonly AppNotification[]>([]);
  readonly unread = this.#unread.asReadonly();
  readonly count = computed(() => this.#unread().length);

  constructor() {
    this.#sse.notification$.pipe(takeUntilDestroyed()).subscribe((n) => this.#received(n));
    effect(() => {
      if (this.#sse.connected()) {
        untracked(() => void this.load());
      }
    });
  }

  /** CA2 y CA3: reemplaza la lista con los avisos no leídos del servidor (también tras entregar un pedido). */
  async load(): Promise<void> {
    try {
      this.#unread.set(await firstValueFrom(this.#http.get<AppNotification[]>(UNREAD_URL)));
    } catch {
      // Sin conexión: se conserva lo que ya hay; se reintenta al reconectar.
    }
  }

  #received(notification: AppNotification): void {
    this.#unread.update((list) => (list.some((n) => n.id === notification.id) ? list : [...list, notification]));
    this.#toast.show(notification.message, 'ready');
    navigator.vibrate?.([...VIBRATION_PATTERN_MS]);
  }
}

import { HttpClient } from '@angular/common/http';
import { Service, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import { AppNotification } from './notification.model';
import { SseClient } from './sse-client';
import { ToastService } from './toast.service';

export const UNREAD_URL = '/api/v1/notifications/unread';
export const readNotificationUrl = (id: number) => `/api/v1/notifications/${id}/read`;
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

  readonly #marking = new Set<number>();
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

  /**
   * HU-15 (D43): marca leídos los avisos `ORDER_CREATED` pendientes (Preparación abre el tablero) con
   * `POST /notifications/{id}/read` y los quita del contador. Un fallo se ignora: `unread` los traerá de nuevo.
   */
  async markOrderCreatedRead(): Promise<void> {
    const pending = this.#unread().filter((n) => n.type === 'ORDER_CREATED' && !this.#marking.has(n.id));
    if (pending.length === 0) {
      return;
    }
    pending.forEach((n) => this.#marking.add(n.id));
    const done = new Set<number>();
    await Promise.all(
      pending.map(async (n) => {
        try {
          await firstValueFrom(this.#http.post<void>(readNotificationUrl(n.id), null));
          done.add(n.id);
        } catch {
          // Se reintenta la próxima vez que se abra el tablero.
        } finally {
          this.#marking.delete(n.id);
        }
      }),
    );
    this.#unread.update((list) => list.filter((n) => !done.has(n.id)));
  }

  #received(notification: AppNotification): void {
    this.#unread.update((list) => (list.some((n) => n.id === notification.id) ? list : [...list, notification]));
    if (notification.type === 'ORDER_CREATED') {
      // Preparación: toast sin vibración (D43).
      this.#toast.show(notification.message, 'info');
      return;
    }
    this.#toast.show(notification.message, 'ready');
    navigator.vibrate?.([...VIBRATION_PATTERN_MS]);
  }
}

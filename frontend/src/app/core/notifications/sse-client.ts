import { HttpClient } from '@angular/common/http';
import { InjectionToken, Service, effect, inject, signal, untracked } from '@angular/core';
import { Observable, Subject, firstValueFrom } from 'rxjs';
import { AppNotification } from './notification.model';

export const STREAM_TICKET_URL = '/api/v1/notifications/stream-ticket';
export const STREAM_URL = '/api/v1/notifications/stream';

export const BACKOFF_MIN_MS = 1000;
export const BACKOFF_MAX_MS = 30_000;

/** Payload del evento `order-changed` (docs/03-api.md). */
export interface OrderChangedEvent {
  readonly orderId: number;
  readonly tableId: number;
  readonly status: string;
}

/** Fábrica de `EventSource`; se reemplaza en pruebas. */
export const EVENT_SOURCE_FACTORY = new InjectionToken<(url: string) => EventSource>('EVENT_SOURCE_FACTORY', {
  providedIn: 'root',
  factory: () => (url: string) => new EventSource(url),
});

/** Espera antes del reintento número `attempt` (0 = primero): 1 s, 2 s, 4 s… hasta 30 s. */
export function backoffDelay(attempt: number): number {
  return Math.min(BACKOFF_MIN_MS * 2 ** attempt, BACKOFF_MAX_MS);
}

/**
 * Cliente SSE (D7, D41). Pide un ticket de un solo uso, abre `EventSource` y, si la conexión se cae, la cierra y
 * reconecta con backoff exponencial pidiendo un ticket nuevo cada vez (el ticket anterior ya se consumió).
 * `reconnections` sube en uno cada vez que se restablece una conexión que ya se había perdido: las vistas lo usan para
 * refrescar sus datos.
 */
@Service()
export class SseClient {
  readonly #http = inject(HttpClient);
  readonly #newSource = inject(EVENT_SOURCE_FACTORY);

  readonly #connected = signal(false);
  readonly #reconnections = signal(0);
  readonly #lastOrderChange = signal<OrderChangedEvent | null>(null);
  readonly #notifications = new Subject<AppNotification>();

  readonly connected = this.#connected.asReadonly();
  readonly reconnections = this.#reconnections.asReadonly();
  /** Último `order-changed` recibido. */
  readonly lastOrderChange = this.#lastOrderChange.asReadonly();
  /** Cada evento `notification` recibido (HU-14, D42); un `Subject` para no perder avisos que llegan seguidos. */
  readonly notification$: Observable<AppNotification> = this.#notifications.asObservable();

  #active = false;
  #everConnected = false;
  #attempt = 0;
  #source: EventSource | null = null;
  #timer: ReturnType<typeof setTimeout> | undefined;
  /** Distingue el ciclo actual de uno cerrado mientras esperaba el ticket. */
  #generation = 0;

  /** Abre el canal (idempotente). Se llama al iniciar sesión. */
  start(): void {
    if (this.#active) {
      return;
    }
    this.#active = true;
    this.#everConnected = false;
    this.#attempt = 0;
    void this.#connect(++this.#generation);
  }

  /** Cierra el canal y cancela cualquier reintento. Se llama al salir. */
  stop(): void {
    this.#active = false;
    this.#generation++;
    clearTimeout(this.#timer);
    this.#close();
    this.#connected.set(false);
  }

  async #connect(generation: number): Promise<void> {
    let ticket: string;
    try {
      ({ ticket } = await firstValueFrom(this.#http.post<{ ticket: string }>(STREAM_TICKET_URL, {})));
    } catch {
      this.#scheduleRetry(generation);
      return;
    }
    if (generation !== this.#generation) {
      return;
    }
    const source = this.#newSource(`${STREAM_URL}?ticket=${encodeURIComponent(ticket)}`);
    this.#source = source;
    source.onopen = () => {
      this.#attempt = 0;
      this.#connected.set(true);
      if (this.#everConnected) {
        this.#reconnections.update((n) => n + 1);
      }
      this.#everConnected = true;
    };
    source.addEventListener('order-changed', (event) => {
      try {
        this.#lastOrderChange.set(JSON.parse((event as MessageEvent<string>).data) as OrderChangedEvent);
      } catch {
        // Evento mal formado: se ignora.
      }
    });
    source.addEventListener('notification', (event) => {
      try {
        this.#notifications.next(JSON.parse((event as MessageEvent<string>).data) as AppNotification);
      } catch {
        // Evento mal formado: se ignora.
      }
    });
    source.onerror = () => {
      // EventSource reintentaría con el mismo ticket (ya usado → 401): se cierra y se reconecta con uno nuevo.
      this.#close();
      this.#connected.set(false);
      this.#scheduleRetry(generation);
    };
  }

  #scheduleRetry(generation: number): void {
    if (generation !== this.#generation) {
      return;
    }
    const delay = backoffDelay(this.#attempt++);
    this.#timer = setTimeout(() => {
      if (generation === this.#generation) {
        void this.#connect(generation);
      }
    }, delay);
  }

  #close(): void {
    this.#source?.close();
    this.#source = null;
  }
}

/**
 * Ejecuta `refresh` cada vez que el canal SSE se restablece tras una caída (CA2). Llamar en un contexto de inyección
 * (constructor de un componente); no se ejecuta en la primera conexión.
 */
export function refreshOnReconnect(refresh: () => void): void {
  const sse = inject(SseClient);
  let seen = untracked(sse.reconnections);
  effect(() => {
    const count = sse.reconnections();
    if (count !== seen) {
      seen = count;
      untracked(refresh);
    }
  });
}

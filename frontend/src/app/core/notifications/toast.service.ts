import { Service, signal } from '@angular/core';

export type ToastKind = 'info' | 'success' | 'ready' | 'error';

export interface Toast {
  readonly id: number;
  readonly kind: ToastKind;
  readonly message: string;
}

const DEFAULT_DURATION_MS = 4500;

/** Avisos al usuario (en lugar de alert()). */
@Service()
export class ToastService {
  readonly #toasts = signal<readonly Toast[]>([]);
  readonly toasts = this.#toasts.asReadonly();
  #nextId = 1;

  show(message: string, kind: ToastKind = 'info', durationMs = DEFAULT_DURATION_MS): number {
    const id = this.#nextId++;
    this.#toasts.update((list) => [...list, { id, kind, message }]);
    if (durationMs > 0) {
      setTimeout(() => this.dismiss(id), durationMs);
    }
    return id;
  }

  info(message: string): number {
    return this.show(message, 'info');
  }

  success(message: string): number {
    return this.show(message, 'success');
  }

  error(message: string): number {
    return this.show(message, 'error', 7000);
  }

  dismiss(id: number): void {
    this.#toasts.update((list) => list.filter((t) => t.id !== id));
  }
}

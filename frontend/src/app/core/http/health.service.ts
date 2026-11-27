import { HttpClient } from '@angular/common/http';
import { Service, inject, signal } from '@angular/core';

export type ApiStatus = 'checking' | 'up' | 'down';

interface HealthResponse {
  readonly status: 'UP' | 'DOWN';
  readonly db: 'UP' | 'DOWN';
}

/** Estado del backend según GET /api/v1/health. */
@Service()
export class HealthService {
  readonly #http = inject(HttpClient);
  readonly #status = signal<ApiStatus>('checking');
  readonly status = this.#status.asReadonly();

  check(): void {
    this.#status.set('checking');
    this.#http.get<HealthResponse>('/api/v1/health').subscribe({
      next: (res) => this.#status.set(res.status === 'UP' && res.db === 'UP' ? 'up' : 'down'),
      error: () => this.#status.set('down'),
    });
  }
}

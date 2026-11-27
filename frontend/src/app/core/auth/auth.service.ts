import { HttpClient } from '@angular/common/http';
import { Service, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Role } from './role';

export interface SessionUser {
  readonly id: number;
  readonly fullName: string;
  readonly role: Role;
}

/** Respuesta de POST /api/v1/auth/login (docs/03-api.md). */
export interface Session {
  readonly token: string;
  readonly expiresAt: string;
  readonly user: SessionUser;
}

export const LOGIN_URL = '/api/v1/auth/login';
export const SESSION_STORAGE_KEY = 'lacava.session';
export const SESSION_EXPIRED_MESSAGE = 'Tu sesión expiró. Inicia sesión de nuevo.';

/** Sesión del usuario: JWT guardado en localStorage. */
@Service()
export class AuthService {
  readonly #http = inject(HttpClient);
  readonly #session = signal<Session | null>(readStoredSession());

  readonly session = this.#session.asReadonly();
  readonly user = computed(() => this.#session()?.user ?? null);
  readonly role = computed<Role | null>(() => this.user()?.role ?? null);

  /** Hay una sesión guardada, vigente o no. */
  hasSession(): boolean {
    return this.#session() !== null;
  }

  /** Hay una sesión guardada y su token no ha vencido. */
  isSessionValid(now = Date.now()): boolean {
    const session = this.#session();
    return session !== null && Date.parse(session.expiresAt) > now;
  }

  token(): string | null {
    return this.#session()?.token ?? null;
  }

  async login(username: string, password: string): Promise<SessionUser> {
    const session = await firstValueFrom(this.#http.post<Session>(LOGIN_URL, { username, password }));
    this.#session.set(session);
    writeStoredSession(session);
    return session.user;
  }

  logout(): void {
    this.#session.set(null);
    writeStoredSession(null);
  }
}

function readStoredSession(): Session | null {
  try {
    const raw = localStorage.getItem(SESSION_STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const session = JSON.parse(raw) as Session;
    return session?.token && session.expiresAt && session.user ? session : null;
  } catch {
    return null;
  }
}

function writeStoredSession(session: Session | null): void {
  try {
    if (session) {
      localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
    } else {
      localStorage.removeItem(SESSION_STORAGE_KEY);
    }
  } catch {
    // Sin almacenamiento disponible (modo privado): la sesión vive solo en memoria.
  }
}

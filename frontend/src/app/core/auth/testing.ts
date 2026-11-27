import { Session, SESSION_STORAGE_KEY } from './auth.service';
import { Role } from './role';

const NAMES: Record<Role, string> = {
  ADMIN: 'Administrador demo',
  CASHIER: 'Carla Gómez',
  WAITER: 'Mateo Ruiz',
  KITCHEN: 'Paula Díaz',
};

/** Sesión de prueba guardada en localStorage (vigente salvo que se indique otro vencimiento). */
export function storeTestSession(role: Role, expiresAt = new Date(Date.now() + 3_600_000).toISOString()): Session {
  const session: Session = {
    token: `token-${role.toLowerCase()}`,
    expiresAt,
    user: { id: 1, fullName: NAMES[role], role },
  };
  localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
  return session;
}

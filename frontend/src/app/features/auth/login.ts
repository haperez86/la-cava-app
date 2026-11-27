import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormField, form, required, submit } from '@angular/forms/signals';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { homePathFor } from '../../core/auth/role';
import { problemMessage } from '../../core/http/problem';

const INVALID_CREDENTIALS = 'Usuario o contraseña incorrectos';

/** Pantalla de inicio de sesión (LCV-HU-01). */
@Component({
  selector: 'app-login',
  imports: [FormField],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  readonly #auth = inject(AuthService);
  readonly #router = inject(Router);

  protected readonly model = signal({ username: '', password: '' });
  protected readonly loginForm = form(this.model, (p) => {
    required(p.username, { message: 'Ingresa tu usuario.' });
    required(p.password, { message: 'Ingresa tu contraseña.' });
  });
  protected readonly error = signal('');
  protected readonly submitting = signal(false);

  protected async onSubmit(): Promise<void> {
    this.error.set('');
    await submit(this.loginForm, async () => {
      this.submitting.set(true);
      try {
        const { username, password } = this.model();
        const user = await this.#auth.login(username, password);
        await this.#router.navigateByUrl(homePathFor(user.role));
      } catch (e) {
        // 401 → credenciales inválidas o usuario inactivo (CA2); red caída o 500 → aviso genérico.
        const invalidCredentials = e instanceof HttpErrorResponse && e.status === 401;
        this.error.set(invalidCredentials ? INVALID_CREDENTIALS : problemMessage(e));
      } finally {
        this.submitting.set(false);
      }
    });
  }
}

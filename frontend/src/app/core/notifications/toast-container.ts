import { Component, inject } from '@angular/core';
import { ToastService } from './toast.service';

@Component({
  selector: 'app-toast-container',
  template: `
    <div class="toast-wrap" aria-live="polite">
      @for (toast of toasts.toasts(); track toast.id) {
        <div class="toast" [class]="toast.kind" [attr.role]="toast.kind === 'error' ? 'alert' : 'status'">
          <span>{{ toast.message }}</span>
          <button type="button" class="close" aria-label="Cerrar aviso" (click)="toasts.dismiss(toast.id)">×</button>
        </div>
      }
    </div>
  `,
  styles: `
    .toast-wrap {
      position: fixed;
      top: 14px;
      right: 14px;
      z-index: 30;
      display: grid;
      gap: 8px;
      max-width: min(360px, calc(100vw - 28px));
    }
    .toast {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 10px;
      background: var(--green);
      color: var(--white);
      padding: 12px 14px;
      border-radius: 11px;
      box-shadow: var(--shadow);
      font-size: 13px;
    }
    .toast.ready { background: var(--ready); }
    .toast.error { background: var(--red); }
    .close {
      background: none;
      border: 0;
      color: inherit;
      font-size: 18px;
      line-height: 1;
      min-width: 24px;
      min-height: 24px;
    }
    @media (max-width: 700px) {
      .toast-wrap { top: auto; bottom: 86px; left: 12px; right: 12px; max-width: none; }
    }
  `,
})
export class ToastContainer {
  protected readonly toasts = inject(ToastService);
}

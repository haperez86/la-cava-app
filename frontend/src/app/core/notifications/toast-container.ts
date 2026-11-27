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
  styleUrl: './toast-container.scss',
})
export class ToastContainer {
  protected readonly toasts = inject(ToastService);
}

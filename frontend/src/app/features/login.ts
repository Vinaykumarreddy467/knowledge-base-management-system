import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, InputTextModule, PasswordModule, ButtonModule, MessageModule],
  template: `
    <div class="flex align-items-center justify-content-center min-h-screen">
      <div class="surface-card p-5 border-round shadow-2 w-full" style="max-width: 380px">
        <h1 class="text-2xl mb-1">KBMS</h1>
        <p class="text-sm text-600 mb-4">Sign in to the knowledge base</p>
        <div class="flex flex-column gap-3">
          <input pInputText type="email" placeholder="Email" [(ngModel)]="email" (keyup.enter)="submit()" />
          <p-password [(ngModel)]="password" [feedback]="false" [toggleMask]="true" placeholder="Password" (keyup.enter)="submit()" styleClass="w-full" inputStyleClass="w-full" />
          @if (error) {
            <p-message severity="error" >{{ error }}</p-message>
          }
          <p-button label="Sign in" [loading]="busy" (onClick)="submit()" styleClass="w-full" />
        </div>
      </div>
    </div>
  `,
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  email = '';
  password = '';
  error = '';
  busy = false;

  submit() {
    if (!this.email || !this.password) {
      this.error = 'Email and password are required.';
      return;
    }
    this.busy = true;
    this.error = '';
    this.auth.login(this.email.trim(), this.password).subscribe({
      next: () => this.router.navigate(['/']),
      error: (err) => {
        this.busy = false;
        this.error = err.error?.message ?? 'Sign in failed. Check the backend is running.';
      },
    });
  }
}
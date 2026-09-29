import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AvatarModule } from 'primeng/avatar';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { AuthService } from '../core/auth.service';
import { Role } from '../core/api';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ButtonModule, AvatarModule, TagModule, TooltipModule],
  template: `
    <div class="flex flex-column min-h-screen">
      <header class="flex align-items-center gap-3 px-4 surface-card border-bottom-1 border-300" style="height: 56px">
        <a routerLink="/" class="font-bold text-xl no-underline text-primary">KBMS</a>
        <nav class="flex align-items-center gap-2 flex-1 min-w-0 overflow-x-auto">
          <a routerLink="/" routerLinkActive="font-bold text-primary" [routerLinkActiveOptions]="{ exact: true }" class="no-underline text-700 px-2 whitespace-nowrap">Dashboard</a>
          <a routerLink="/articles" routerLinkActive="font-bold text-primary" class="no-underline text-700 px-2 whitespace-nowrap">Articles</a>
          <a routerLink="/documents" routerLinkActive="font-bold text-primary" class="no-underline text-700 px-2 whitespace-nowrap">Documents</a>
          <a routerLink="/search" routerLinkActive="font-bold text-primary" class="no-underline text-700 px-2 whitespace-nowrap">Search</a>
          <a routerLink="/assistant" routerLinkActive="font-bold text-primary" class="no-underline text-700 px-2 whitespace-nowrap">Assistant</a>
          @if (auth.isAdmin()) {
            <a routerLink="/categories" routerLinkActive="font-bold text-primary" class="no-underline text-700 px-2 whitespace-nowrap">Categories</a>
            <a routerLink="/tags" routerLinkActive="font-bold text-primary" class="no-underline text-700 px-2 whitespace-nowrap">Tags</a>
            <a routerLink="/admin/users" routerLinkActive="font-bold text-primary" class="no-underline text-700 px-2 whitespace-nowrap">Users</a>
            <a routerLink="/admin/audit" routerLinkActive="font-bold text-primary" class="no-underline text-700 px-2 whitespace-nowrap">Audit</a>
          }
        </nav>
        <div class="flex align-items-center gap-2 min-w-0">
          <p-avatar [label]="initials()" shape="circle" size="normal" />
          <span class="text-sm text-700 truncate">{{ auth.user()?.fullName || auth.user()?.email }}</span>
          @if (auth.user()?.role; as role) {
            <p-tag
              [value]="role"
              [severity]="roleSeverity(role)"
              class="text-xs whitespace-nowrap"
              [attr.aria-label]="'Role: ' + role"
              pTooltip="Signed in as {{ role }}"
            />
          }
          <p-button icon="pi pi-sign-out" severity="secondary" text rounded (onClick)="auth.logout()" pTooltip="Sign out" />
        </div>
      </header>
      <main class="flex-1 p-4">
        <router-outlet />
      </main>
    </div>
  `,
})
export class ShellComponent {
  auth = inject(AuthService);

  initials(): string {
    const name = this.auth.user()?.fullName?.trim();
    if (name) {
      return name
        .split(/\s+/)
        .slice(0, 2)
        .map((p) => p[0]?.toUpperCase())
        .join('');
    }
    return (this.auth.user()?.email?.[0] ?? '?').toUpperCase();
  }

  /** Display only. Never used for access control, which is enforced by the backend. */
  roleSeverity(role: Role): 'danger' | 'warn' | 'secondary' {
    if (role === 'ADMIN') return 'danger';
    if (role === 'EDITOR') return 'warn';
    return 'secondary';
  }
}
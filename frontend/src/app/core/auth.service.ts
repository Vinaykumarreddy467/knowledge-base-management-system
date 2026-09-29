import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { API, LoginResponse, UserView } from './api';

const TOKEN_KEY = 'kbms.token';
const USER_KEY = 'kbms.user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private tokenSig = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  private userSig = signal<UserView | null>(this.readUser());

  readonly user = this.userSig.asReadonly();
  readonly isAdmin = computed(() => this.userSig()?.role === 'ADMIN');
  readonly isStaff = computed(() => {
    const role = this.userSig()?.role;
    return role === 'ADMIN' || role === 'EDITOR';
  });

  get token(): string | null {
    return this.tokenSig();
  }

  login(email: string, password: string) {
    return this.http
      .post<LoginResponse>(`${API}/auth/login`, { email, password })
      .pipe(
        tap((r) => {
          localStorage.setItem(TOKEN_KEY, r.accessToken);
          localStorage.setItem(USER_KEY, JSON.stringify(r.user));
          this.tokenSig.set(r.accessToken);
          this.userSig.set(r.user);
        }),
      );
  }

  logout() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.tokenSig.set(null);
    this.userSig.set(null);
    this.router.navigate(['/login']);
  }

  private readUser(): UserView | null {
    try {
      return JSON.parse(localStorage.getItem(USER_KEY) ?? 'null');
    } catch {
      return null;
    }
  }
}
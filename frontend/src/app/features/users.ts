import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { PaginatorModule } from 'primeng/paginator';
import { PasswordModule } from 'primeng/password';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { MessageModule } from 'primeng/message';
import { AuthService } from '../core/auth.service';
import { API, PageResponse, Role, UserView } from '../core/api';

@Component({
  selector: 'app-users',
  imports: [DatePipe, FormsModule, ButtonModule, CardModule, DialogModule, SelectModule, InputTextModule, PaginatorModule, PasswordModule, TableModule, TagModule, ToggleSwitchModule, MessageModule],
  template: `
    <div class="flex align-items-center justify-content-between mb-3">
      <h2 class="mt-0 mb-0">Users</h2>
      <p-button label="New user" icon="pi pi-plus" (onClick)="open(null)" />
    </div>
    @if (error) { <p-message severity="error" class="mb-2" >{{ error }}</p-message> }
    <p-card>
      <p-table [value]="page().items" [loading]="loading">
        <ng-template #header>
          <tr><th>Email</th><th>Name</th><th>Role</th><th>Active</th><th>Created</th><th></th></tr>
        </ng-template>
        <ng-template #body let-row>
          <tr>
            <td>{{ row.email }}</td>
            <td>{{ row.fullName }}</td>
            <td><p-tag [value]="row.role" [severity]="row.role === 'ADMIN' ? 'danger' : row.role === 'EDITOR' ? 'warn' : 'secondary'" /></td>
            <td><p-tag [value]="row.active ? 'ACTIVE' : 'INACTIVE'" [severity]="row.active ? 'success' : 'secondary'" /></td>
            <td>{{ row.createdAt | date: 'mediumDate' }}</td>
            <td class="text-right">
              <p-button icon="pi pi-pencil" text (onClick)="open(row)" />
              <p-button icon="pi pi-trash" text severity="danger" (onClick)="remove(row)" [disabled]="row.id === me?.id" />
            </td>
          </tr>
        </ng-template>
      </p-table>
      <p-paginator [rows]="size" [totalRecords]="page().totalItems" [first]="pageIndex * size" (onPageChange)="onPage($event)" />
    </p-card>
    <p-dialog [(visible)]="show" [header]="editing ? 'Edit user' : 'New user'" [modal]="true" [style]="{ width: '460px' }">
      <div class="flex flex-column gap-3">
        <input pInputText placeholder="Email" [(ngModel)]="form.email" [readonly]="!!editing" />
        <input pInputText placeholder="Full name" [(ngModel)]="form.fullName" maxlength="200" />
        <p-password [(ngModel)]="form.password" placeholder="Password" [feedback]="false" styleClass="w-full" inputStyleClass="w-full" [placeholder]="editing ? 'Leave blank to keep current' : 'At least 12 characters'" />
        <p-select [options]="roleOptions" [(ngModel)]="form.role" placeholder="Role" styleClass="w-full" />
        @if (editing) {
          <div class="flex align-items-center gap-2">
            <p-toggleswitch [(ngModel)]="form.active" />
            <label>Active</label>
          </div>
        }
        @if (error) { <p-message severity="error" >{{ error }}</p-message> }
      </div>
      <ng-template #footer>
        <p-button label="Cancel" severity="secondary" (onClick)="show = false" />
        <p-button label="Save" (onClick)="save()" [loading]="busy" />
      </ng-template>
    </p-dialog>
  `,
})
export class UsersComponent implements OnInit {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  me = this.auth.user();
  page = signal<PageResponse<UserView>>({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  loading = true;
  show = false;
  editing: UserView | null = null;
  busy = false;
  error = '';
  pageIndex = 0;
  size = 25;
  roleOptions = [
    { label: 'ADMIN', value: 'ADMIN' },
    { label: 'EDITOR', value: 'EDITOR' },
    { label: 'VIEWER', value: 'VIEWER' },
  ];
  form = { email: '', fullName: '', password: '', role: 'VIEWER' as Role, active: true };

  ngOnInit() { this.load(); }
  load() {
    this.loading = true;
    this.http.get<PageResponse<UserView>>(`${API}/admin/users`, { params: { page: this.pageIndex, size: this.size } }).subscribe({
      next: (p) => { this.page.set(p); this.loading = false; },
      error: () => (this.loading = false),
    });
  }
  open(u: UserView | null) {
    this.error = '';
    this.editing = u;
    this.form = u ? { email: u.email, fullName: u.fullName, password: '', role: u.role, active: u.active } : { email: '', fullName: '', password: '', role: 'VIEWER', active: true };
    this.show = true;
  }
  save() {
    this.error = '';
    if (!this.editing) {
      if (!this.form.email.trim() || !this.form.fullName.trim() || this.form.password.length < 12) {
        this.error = 'Email, full name and a password of at least 12 characters are required.';
        return;
      }
    }
    this.busy = true;
    if (this.editing) {
      const body: any = { fullName: this.form.fullName.trim() || null, role: this.form.role, active: this.form.active };
      if (this.form.password) body.password = this.form.password;
      this.http.put<UserView>(`${API}/admin/users/${this.editing.id}`, body).subscribe({
        next: () => { this.busy = false; this.show = false; this.load(); },
        error: (e) => { this.busy = false; this.error = e.error?.message ?? 'Failed to save user.'; },
      });
    } else {
      this.http.post<UserView>(`${API}/admin/users`, { email: this.form.email.trim(), fullName: this.form.fullName.trim(), password: this.form.password, role: this.form.role }).subscribe({
        next: () => { this.busy = false; this.show = false; this.load(); },
        error: (e) => { this.busy = false; this.error = e.error?.message ?? 'Failed to create user.'; },
      });
    }
  }
  remove(u: UserView) {
    if (!confirm(`Delete ${u.email}?`)) return;
    this.http.delete(`${API}/admin/users/${u.id}`).subscribe({ next: () => this.load(), error: (e) => (this.error = e.error?.message ?? 'Delete failed.') });
  }
  onPage(e: any) { this.pageIndex = e.first / e.rows; this.size = e.rows; this.load(); }
}

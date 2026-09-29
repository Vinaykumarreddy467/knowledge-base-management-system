import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { MessageModule } from 'primeng/message';
import { API, Category } from '../core/api';

@Component({
  selector: 'app-categories',
  imports: [FormsModule, ButtonModule, CardModule, DialogModule, InputTextModule, TableModule, TagModule, TextareaModule, MessageModule],
  template: `
    <div class="flex align-items-center justify-content-between mb-3">
      <h2 class="mt-0 mb-0">Categories</h2>
      <p-button label="New category" icon="pi pi-plus" (onClick)="open(null)" />
    </div>
    @if (error) { <p-message severity="error" class="mb-2" >{{ error }}</p-message> }
    <p-card>
      <p-table [value]="items()" [loading]="loading">
        <ng-template #header>
          <tr><th>Name</th><th>Description</th><th>Active</th><th></th></tr>
        </ng-template>
        <ng-template #body let-row>
          <tr>
            <td>{{ row.name }}</td>
            <td>{{ row.description || '-' }}</td>
            <td><p-tag [value]="row.active ? 'ACTIVE' : 'INACTIVE'" [severity]="row.active ? 'success' : 'secondary'" /></td>
            <td class="text-right"><p-button icon="pi pi-pencil" text (onClick)="open(row)" /></td>
          </tr>
        </ng-template>
      </p-table>
    </p-card>
    <p-dialog [(visible)]="show" [header]="editing ? 'Edit category' : 'New category'" [modal]="true" [style]="{ width: '480px' }">
      <div class="flex flex-column gap-3">
        <input pInputText placeholder="Name" [(ngModel)]="form.name" maxlength="120" />
        <textarea pTextarea placeholder="Description" [(ngModel)]="form.description" rows="3" maxlength="1000" class="w-full"></textarea>
        @if (editing) {
          <div class="flex align-items-center gap-2">
            <input type="checkbox" id="active" [(ngModel)]="form.active" />
            <label for="active">Active</label>
          </div>
        }
        @if (error) { <p-message severity="error" >{{ error }}</p-message> }
      </div>
      <ng-template #footer>
        <p-button label="Cancel" severity="secondary" (onClick)="close()" />
        <p-button label="Save" (onClick)="save()" [loading]="busy" />
      </ng-template>
    </p-dialog>
  `,
})
export class CategoriesComponent implements OnInit {
  private http = inject(HttpClient);
  items = signal<Category[]>([]);
  loading = true;
  show = false;
  editing: Category | null = null;
  busy = false;
  error = '';
  form = { name: '', description: '', active: true };

  ngOnInit() { this.load(); }
  load() {
    this.loading = true;
    this.http.get<Category[]>(`${API}/categories/all`).subscribe({ next: (c) => { this.items.set(c); this.loading = false; }, error: () => (this.loading = false) });
  }
  open(c: Category | null) {
    this.error = '';
    this.editing = c;
    this.form = c ? { name: c.name, description: c.description ?? '', active: c.active } : { name: '', description: '', active: true };
    this.show = true;
  }
  close() { this.show = false; }
  save() {
    if (!this.form.name.trim()) { this.error = 'Name is required.'; return; }
    this.busy = true;
    this.error = '';
    const body = { name: this.form.name.trim(), description: this.form.description.trim() || null };
    if (this.editing) {
      this.http.put<Category>(`${API}/categories/${this.editing.id}`, { ...body, active: this.form.active }).subscribe({ next: () => { this.close(); this.load(); this.busy = false; }, error: (e) => { this.busy = false; this.error = e.error?.message ?? 'Failed.'; } });
    } else {
      this.http.post<Category>(`${API}/categories`, body).subscribe({ next: () => { this.close(); this.load(); this.busy = false; }, error: (e) => { this.busy = false; this.error = e.error?.message ?? 'Failed.'; } });
    }
  }
}
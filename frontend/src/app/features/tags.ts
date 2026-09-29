import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { MessageModule } from 'primeng/message';
import { API, Tag } from '../core/api';

@Component({
  selector: 'app-tags',
  imports: [FormsModule, ButtonModule, CardModule, DialogModule, InputTextModule, TableModule, MessageModule],
  template: `
    <div class="flex align-items-center justify-content-between mb-3">
      <h2 class="mt-0 mb-0">Tags</h2>
      <p-button label="New tag" icon="pi pi-plus" (onClick)="open(null)" />
    </div>
    <p-card>
      <p-table [value]="items()" [loading]="loading">
        <ng-template #header><tr><th>Name</th><th></th></tr></ng-template>
        <ng-template #body let-row>
          <tr>
            <td>{{ row.name }}</td>
            <td class="text-right"><p-button icon="pi pi-pencil" text (onClick)="open(row)" /></td>
          </tr>
        </ng-template>
      </p-table>
    </p-card>
    <p-dialog [(visible)]="show" [header]="editing ? 'Edit tag' : 'New tag'" [modal]="true" [style]="{ width: '380px' }">
      <input pInputText placeholder="Name" [(ngModel)]="form.name" maxlength="80" class="w-full" />
      @if (error) { <p-message severity="error" >{{ error }}</p-message> }
      <ng-template #footer>
        <p-button label="Cancel" severity="secondary" (onClick)="show = false" />
        <p-button label="Save" (onClick)="save()" [loading]="busy" />
      </ng-template>
    </p-dialog>
  `,
})
export class TagsComponent implements OnInit {
  private http = inject(HttpClient);
  items = signal<Tag[]>([]);
  loading = true;
  show = false;
  editing: Tag | null = null;
  busy = false;
  error = '';
  form = { name: '' };

  ngOnInit() { this.load(); }
  load() {
    this.loading = true;
    this.http.get<Tag[]>(`${API}/tags/all`).subscribe({ next: (t) => { this.items.set(t); this.loading = false; }, error: () => (this.loading = false) });
  }
  open(t: Tag | null) { this.error = ''; this.editing = t; this.form = { name: t?.name ?? '' }; this.show = true; }
  save() {
    if (!this.form.name.trim()) { this.error = 'Name is required.'; return; }
    this.busy = true; this.error = '';
    if (this.editing) {
      this.http.put<Tag>(`${API}/tags/${this.editing.id}`, { name: this.form.name.trim() }).subscribe({ next: () => { this.show = false; this.load(); this.busy = false; }, error: (e) => { this.busy = false; this.error = e.error?.message ?? 'Failed.'; } });
    } else {
      this.http.post<Tag>(`${API}/tags`, { name: this.form.name.trim() }).subscribe({ next: () => { this.show = false; this.load(); this.busy = false; }, error: (e) => { this.busy = false; this.error = e.error?.message ?? 'Failed.'; } });
    }
  }
}
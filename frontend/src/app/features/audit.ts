import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { CardModule } from 'primeng/card';
import { PaginatorModule } from 'primeng/paginator';
import { TableModule } from 'primeng/table';
import { API, AuditView, PageResponse } from '../core/api';

@Component({
  selector: 'app-audit',
  imports: [DatePipe, CardModule, PaginatorModule, TableModule],
  template: `
    <h2 class="mt-0">Audit log</h2>
    <p-card>
      <p-table [value]="page().items" [loading]="loading" [scrollable]="true" scrollHeight="flex">
        <ng-template #header>
          <tr><th>When</th><th>Actor</th><th>Action</th><th>Entity</th><th>Detail</th></tr>
        </ng-template>
        <ng-template #body let-row>
          <tr>
            <td class="text-nowrap">{{ row.createdAt | date: 'medium' }}</td>
            <td>{{ row.actorEmail }}</td>
            <td class="text-nowrap">{{ row.action }}</td>
            <td class="text-nowrap">{{ row.entityType }}#{{ row.entityId }}</td>
            <td class="text-sm text-600">{{ row.detail }}</td>
          </tr>
        </ng-template>
        <ng-template #emptydata>
          <tr><td colspan="5" class="text-center text-600">No audit events yet.</td></tr>
        </ng-template>
      </p-table>
      <p-paginator [rows]="size" [totalRecords]="page().totalItems" [first]="pageIndex * size" (onPageChange)="onPage($event)" />
    </p-card>
  `,
})
export class AuditComponent implements OnInit {
  private http = inject(HttpClient);
  page = signal<PageResponse<AuditView>>({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  loading = true;
  pageIndex = 0;
  size = 50;

  ngOnInit() { this.load(); }
  load() {
    this.loading = true;
    this.http.get<PageResponse<AuditView>>(`${API}/admin/audit`, { params: { page: this.pageIndex, size: this.size } }).subscribe({
      next: (p) => { this.page.set(p); this.loading = false; },
      error: () => (this.loading = false),
    });
  }
  onPage(e: any) { this.pageIndex = e.first / e.rows; this.size = e.rows; this.load(); }
}
import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { PaginatorModule } from 'primeng/paginator';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { MessageModule } from 'primeng/message';
import { API, DocumentResponse, DocumentStatus, PageResponse } from '../core/api';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-documents',
  imports: [DatePipe, FormsModule, ButtonModule, CardModule, DialogModule, SelectModule, PaginatorModule, TableModule, TagModule, TooltipModule, MessageModule],
  template: `
    <div class="flex align-items-center justify-content-between mb-3">
      <h2 class="mt-0 mb-0">Documents</h2>
      <p-button label="Upload" icon="pi pi-upload" (onClick)="file.click()" [disabled]="!auth.isStaff() || uploading" [loading]="uploading" />
      <input #file type="file" hidden (change)="onFile($event)" />
    </div>
    @if (error) { <p-message severity="error" class="mb-2" >{{ error }}</p-message> }
    @if (!auth.isStaff()) { <p-message severity="info" class="mb-2">Viewers can browse and read documents but cannot upload or process them.</p-message> }
    <p-card>
      <div class="grid mb-3">
        <div class="col-12 md:col-4">
          <p-select [options]="statusOptions" [(ngModel)]="status" placeholder="Filter by status" [showClear]="true" (onChange)="load()" styleClass="w-full" />
        </div>
      </div>
      <p-table [value]="page().items" [loading]="loading">
        <ng-template #header>
          <tr><th>Name</th><th>Type</th><th>Size</th><th>Status</th><th>Chunks</th><th>Uploaded</th><th></th></tr>
        </ng-template>
        <ng-template #body let-row>
          <tr>
            <td>
              {{ row.originalName }}
              @if (row.failureReason) { <div class="text-xs text-red-500 line-height-3">{{ row.failureReason }}</div> }
            </td>
            <td class="text-xs">{{ row.contentType }}</td>
            <td>{{ formatSize(row.sizeBytes) }}</td>
            <td><p-tag [value]="row.status" [severity]="severity(row.status)" /></td>
            <td>{{ row.chunkCount }}</td>
            <td>{{ row.uploadedAt | date: 'medium' }}</td>
            <td class="text-right">
              <p-button icon="pi pi-eye" text (onClick)="view(row)" pTooltip="View extracted text" />
              @if (auth.isStaff()) {
                <p-button icon="pi pi-refresh" text (onClick)="process(row)" pTooltip="Reprocess" [loading]="processingId === row.id" />
                <p-button icon="pi pi-trash" text severity="danger" (onClick)="remove(row)" pTooltip="Delete" />
              }
            </td>
          </tr>
        </ng-template>
        <ng-template #emptydata>
          <tr><td colspan="7" class="text-center text-600">No documents yet.</td></tr>
        </ng-template>
      </p-table>
      <p-paginator [rows]="size" [totalRecords]="page().totalItems" [first]="pageIndex * size" (onPageChange)="onPage($event)" />
    </p-card>
    <p-dialog [(visible)]="showContent" header="Extracted text" [modal]="true" [style]="{ width: '900px' }" [maximizable]="true">
      <pre class="article-content text-sm m-0" style="max-height: 70vh; overflow: auto">{{ content }}</pre>
    </p-dialog>
  `,
})
export class DocumentsComponent implements OnInit {
  private http = inject(HttpClient);
  auth = inject(AuthService);
  page = signal<PageResponse<DocumentResponse>>({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  loading = true;
  uploading = false;
  processingId: number | null = null;
  error = '';
  status: DocumentStatus | null = null;
  pageIndex = 0;
  size = 20;
  statusOptions = [
    { label: 'UPLOADED', value: 'UPLOADED' },
    { label: 'PROCESSING', value: 'PROCESSING' },
    { label: 'PROCESSED', value: 'PROCESSED' },
    { label: 'FAILED', value: 'FAILED' },
  ];
  showContent = false;
  content = '';

  ngOnInit() { this.load(); }
  load() {
    this.loading = true;
    const params: any = { page: this.pageIndex, size: this.size };
    if (this.status) params.status = this.status;
    this.http.get<PageResponse<DocumentResponse>>(`${API}/documents`, { params }).subscribe({
      next: (p) => { this.page.set(p); this.loading = false; },
      error: () => (this.loading = false),
    });
  }
  onFile(e: any) {
    const f = e.target.files?.[0];
    if (!f) return;
    const fd = new FormData();
    fd.append('file', f);
    this.uploading = true; this.error = '';
    this.http.post<DocumentResponse>(`${API}/documents`, fd).subscribe({
      next: () => { this.uploading = false; e.target.value = ''; this.load(); },
      error: (err) => { this.uploading = false; e.target.value = ''; this.error = err.error?.message ?? 'Upload failed.'; },
    });
  }
  process(row: DocumentResponse) {
    this.processingId = row.id;
    this.http.post(`${API}/documents/${row.id}/process`, {}).subscribe({
      next: () => { this.processingId = null; this.load(); },
      error: (e) => { this.processingId = null; this.error = e.error?.message ?? 'Failed to process document.'; },
    });
  }
  view(row: DocumentResponse) {
    this.http.get(`${API}/documents/${row.id}/content`, { responseType: 'text' }).subscribe({
      next: (t) => { this.content = t || 'No extracted text yet.'; this.showContent = true; },
      error: (e) => (this.error = e.error?.message ?? 'Could not load content.'),
    });
  }
  remove(row: DocumentResponse) {
    if (!confirm(`Delete ${row.originalName}?`)) return;
    this.http.delete(`${API}/documents/${row.id}`).subscribe({ next: () => this.load(), error: (e) => (this.error = e.error?.message ?? 'Delete failed.') });
  }
  onPage(e: any) { this.pageIndex = e.first / e.rows; this.size = e.rows; this.load(); }
  formatSize(b: number) { if (b < 1024) return `${b} B`; if (b < 1048576) return `${(b / 1024).toFixed(1)} KB`; return `${(b / 1048576).toFixed(1)} MB`; }
  severity(s: DocumentStatus) { if (s === 'PROCESSED') return 'success'; if (s === 'FAILED') return 'danger'; if (s === 'PROCESSING') return 'warn'; return 'secondary'; }
}

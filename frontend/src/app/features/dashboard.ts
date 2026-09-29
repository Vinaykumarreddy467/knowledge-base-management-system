import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { API, Dashboard } from '../core/api';

@Component({
  selector: 'app-dashboard',
  imports: [DatePipe, CardModule, TableModule, TagModule, RouterLink],
  template: `
    <h2 class="mt-0">Dashboard</h2>
    <div class="grid">
      @for (stat of stats; track stat.label) {
        <div class="col-12 md:col-6 lg:col-3">
          <p-card>
            <div class="text-3xl font-bold">{{ stat.value }}</div>
            <div class="text-sm text-600">{{ stat.label }}</div>
          </p-card>
        </div>
      }
    </div>
    <p-card header="Recent documents">
      <p-table [value]="data()?.recentDocuments ?? []" [loading]="loading">
        <ng-template #header>
          <tr>
            <th>Name</th>
            <th>Status</th>
            <th>Uploaded</th>
          </tr>
        </ng-template>
        <ng-template #body let-row>
          <tr>
            <td>{{ row.name }}</td>
            <td><p-tag [value]="row.status" [severity]="statusSeverity(row.status)" /></td>
            <td>{{ row.uploadedAt | date: 'medium' }}</td>
          </tr>
        </ng-template>
        <ng-template #emptydata>
          <tr><td colspan="3" class="text-center text-600">No documents yet.</td></tr>
        </ng-template>
      </p-table>
    </p-card>
  `,
})
export class DashboardComponent implements OnInit {
  private http = inject(HttpClient);
  data = signal<Dashboard | null>(null);
  loading = true;

  get stats() {
    const d = this.data();
    return [
      { label: 'Articles', value: d?.articles ?? 0 },
      { label: 'Published', value: d?.publishedArticles ?? 0 },
      { label: 'Documents', value: d?.documents ?? 0 },
      { label: 'Processed', value: d?.processedDocuments ?? 0 },
      { label: 'Categories', value: d?.categories ?? 0 },
      { label: 'Tags', value: d?.tags ?? 0 },
      { label: 'AI questions', value: d?.aiQuestions ?? 0 },
      { label: 'Failed docs', value: d?.failedDocuments ?? 0 },
    ];
  }

  ngOnInit() {
    this.http.get<Dashboard>(`${API}/dashboard`).subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading = false;
      },
      error: () => (this.loading = false),
    });
  }

  statusSeverity(status: string): 'success' | 'warn' | 'danger' | 'secondary' {
    if (status === 'PROCESSED') return 'success';
    if (status === 'FAILED') return 'danger';
    if (status === 'PROCESSING') return 'warn';
    return 'secondary';
  }
}
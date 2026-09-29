import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { PaginatorModule } from 'primeng/paginator';
import { TagModule } from 'primeng/tag';
import { API, ArticleStatus, ArticleSummary, Category, PageResponse, Tag } from '../core/api';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-article-list',
  imports: [CommonModule, DatePipe, FormsModule, ButtonModule, CardModule, SelectModule, InputTextModule, PaginatorModule, TagModule, RouterLink],
  template: `
    <div class="flex align-items-center justify-content-between mb-3">
      <h2 class="mt-0 mb-0">Articles</h2>
      @if (auth.isStaff()) {
        <p-button label="New article" icon="pi pi-plus" routerLink="/articles/new" />
      }
    </div>
    <p-card>
      <div class="grid mb-3">
        <div class="col-12 md:col-4">
          <input pInputText type="text" placeholder="Search title/summary" [(ngModel)]="search" (keyup.enter)="load()" class="w-full" />
        </div>
        <div class="col-12 md:col-2">
          <p-select [options]="statusOptions" [(ngModel)]="status" placeholder="Status" [showClear]="true" styleClass="w-full" />
        </div>
        <div class="col-12 md:col-2">
          <p-select [options]="categoryOptions" [(ngModel)]="categoryId" placeholder="Category" [showClear]="true" styleClass="w-full" optionLabel="name" optionValue="id" />
        </div>
        <div class="col-12 md:col-2">
          <p-select [options]="tagOptions" [(ngModel)]="tagId" placeholder="Tag" [showClear]="true" styleClass="w-full" optionLabel="name" optionValue="id" />
        </div>
        <div class="col-12 md:col-2 flex gap-2">
          <p-button label="Apply" (onClick)="load()" styleClass="w-full" />
          <p-button label="Clear" severity="secondary" (onClick)="clear()" styleClass="w-full" />
        </div>
      </div>
      <div class="overflow-auto">
        <table class="w-full border-collapse">
          <thead>
            <tr class="bg-surface-100 dark:bg-surface-800">
              <th class="p-3 text-left">Title</th>
              <th class="p-3 text-left">Status</th>
              <th class="p-3 text-left">Category</th>
              <th class="p-3 text-left">Updated</th>
              <th class="p-3 text-right"></th>
            </tr>
          </thead>
          <tbody>
            @for (row of page().items; track row.id) {
              <tr class="border-t border-surface-200 dark:border-surface-700 hover:bg-surface-50 dark:hover:bg-surface-800">
                <td class="p-3">
                  <a [routerLink]="['/articles', row.id]" class="font-medium">{{ row.title }}</a>
                  <div class="text-xs text-600 line-height-3">{{ row.summary }}</div>
                </td>
                <td class="p-3">
                  <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium"
                        [ngClass]="{
                          'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200': row.status === 'PUBLISHED',
                          'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200': row.status === 'ARCHIVED',
                          'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200': row.status === 'DRAFT'
                        }">
                    {{ row.status }}
                  </span>
                </td>
                <td class="p-3">{{ row.categoryName || '-' }}</td>
                <td class="p-3">{{ row.updatedAt | date: 'medium' }}</td>
                <td class="p-3 text-right">
                  <p-button icon="pi pi-pencil" text [routerLink]="['/articles', row.id]" />
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="5" class="p-3 text-center text-600">No articles found.</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <p-paginator [rows]="size" [totalRecords]="page().totalItems" [first]="pageIndex * size" (onPageChange)="onPage($event)" />
    </p-card>
  `,
})
export class ArticleListComponent implements OnInit {
  private http = inject(HttpClient);
  auth = inject(AuthService);

  search = '';
  status: ArticleStatus | null = null;
  categoryId: number | null = null;
  tagId: number | null = null;
  page = signal<PageResponse<ArticleSummary>>({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  loading = true;
  pageIndex = 0;
  size = 20;

  statusOptions = [
    { label: 'DRAFT', value: 'DRAFT' },
    { label: 'PUBLISHED', value: 'PUBLISHED' },
    { label: 'ARCHIVED', value: 'ARCHIVED' },
  ];
  categoryOptions: Category[] = [];
  tagOptions: Tag[] = [];

  ngOnInit() {
    this.http.get<Category[]>(`${API}/categories/all`).subscribe((c) => (this.categoryOptions = c));
    this.http.get<Tag[]>(`${API}/tags/all`).subscribe((t) => (this.tagOptions = t));
    this.load();
  }

  load() {
    this.loading = true;
    const params: any = { page: this.pageIndex, size: this.size };
    if (this.search) params.search = this.search;
    if (this.status) params.status = this.status;
    if (this.categoryId) params.categoryId = this.categoryId;
    if (this.tagId) params.tagId = this.tagId;
    this.http.get<PageResponse<ArticleSummary>>(`${API}/articles`, { params }).subscribe({
      next: (p) => {
        console.debug('[ArticleList] load next', p?.items?.length, p?.totalItems);
        this.page.set(p);
        this.loading = false;
      },
      error: (e) => {
        console.error('[ArticleList] load error', e);
        this.loading = false;
      },
    });
  }

  clear() {
    this.search = '';
    this.status = null;
    this.categoryId = null;
    this.tagId = null;
    this.pageIndex = 0;
    this.load();
  }

  onPage(e: any) {
    this.pageIndex = e.first / e.rows;
    this.size = e.rows;
    this.load();
  }

  statusSeverity(s: ArticleStatus) {
    if (s === 'PUBLISHED') return 'success';
    if (s === 'ARCHIVED') return 'danger';
    return 'warn';
  }
}
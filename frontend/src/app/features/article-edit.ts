import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { MultiSelectModule } from 'primeng/multiselect';
import { ProgressBarModule } from 'primeng/progressbar';
import { TextareaModule } from 'primeng/textarea';
import { MessageModule } from 'primeng/message';
import { API, ArticleDetail, ArticleStatus, ArticleUpsert, Category, Tag } from '../core/api';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-article-edit',
  imports: [DatePipe, FormsModule, ButtonModule, CardModule, SelectModule, InputTextModule, MultiSelectModule, ProgressBarModule, TextareaModule, MessageModule],
  template: `
    <div class="flex align-items-center justify-content-between mb-3">
      <h2 class="mt-0 mb-0">{{ id ? 'Edit article' : 'New article' }}</h2>
      <div class="flex gap-2">
        @if (id && auth.isStaff()) {
          <p-button label="Delete" severity="danger" icon="pi pi-trash" (onClick)="remove()" [loading]="busy" />
        }
        <p-button label="Cancel" severity="secondary" routerLink="/articles" />
        @if (auth.isStaff()) {
          <p-button label="Save" icon="pi pi-save" (onClick)="save()" [loading]="busy" />
        }
      </div>
    </div>
    @if (error()) { <p-message severity="error" class="mb-2" >{{ error() }}</p-message> }
    @if (loaded()) {
    <p-card>
      <div class="grid">
        <div class="col-12">
          <label class="block mb-1">Title</label>
          <input pInputText [(ngModel)]="form.title" class="w-full" maxlength="300" />
        </div>
        <div class="col-12">
          <label class="block mb-1">Summary</label>
          <textarea pTextarea [(ngModel)]="form.summary" rows="2" class="w-full" maxlength="1000"></textarea>
        </div>
        <div class="col-12 md:col-4">
          <label class="block mb-1">Category</label>
          <p-select [options]="categories" [(ngModel)]="form.categoryId" [showClear]="true" optionLabel="name" optionValue="id" placeholder="None" styleClass="w-full" />
        </div>
        <div class="col-12 md:col-4">
          <label class="block mb-1">Tags</label>
          <p-multiselect [options]="tags" [(ngModel)]="form.tagIds" optionLabel="name" optionValue="id" display="chip" styleClass="w-full" />
        </div>
        <div class="col-12 md:col-4">
          <label class="block mb-1">Status</label>
          <p-select [options]="statusOptions" [(ngModel)]="form.status" styleClass="w-full" />
        </div>
        <div class="col-12">
          <label class="block mb-1">Content</label>
          <textarea pTextarea [(ngModel)]="form.content" rows="20" class="w-full" maxlength="500000"></textarea>
        </div>
      </div>
    </p-card>
    } @else {
      <p-progressbar mode="indeterminate" styleClass="w-full" ariaLabel="Loading article" />
    }
  `,
})
export class ArticleEditComponent implements OnInit {
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  auth = inject(AuthService);

  id: number | null = null;
  busy = false;
  error = signal('');
  /**
   * This app is zoneless, so plain fields assigned in the HTTP callbacks never schedule a render.
   * This signal is read by the template, so flipping it renders the loaded article, the dropdown
   * options and any load error. It flips once, when every initial request has settled.
   */
  loaded = signal(false);
  private pending = 0;
  private settle() {
    if (--this.pending === 0) this.loaded.set(true);
  }
  categories: Category[] = [];
  tags: Tag[] = [];
  statusOptions = [
    { label: 'DRAFT', value: 'DRAFT' },
    { label: 'PUBLISHED', value: 'PUBLISHED' },
    { label: 'ARCHIVED', value: 'ARCHIVED' },
  ];
  form: ArticleUpsert = {
    title: '',
    summary: '',
    content: '',
    categoryId: null,
    tagIds: [],
    status: 'DRAFT',
  };

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    this.pending = id ? 3 : 2;
    this.http.get<Category[]>(`${API}/categories/all`).subscribe({
      next: (c) => this.categories = c,
      complete: () => this.settle(),
    });
    this.http.get<Tag[]>(`${API}/tags/all`).subscribe({
      next: (t) => this.tags = t,
      complete: () => this.settle(),
    });
    if (id) {
      this.id = +id;
      this.http.get<ArticleDetail>(`${API}/articles/${this.id}`).subscribe({
        next: (d) => {
          this.form = {
            title: d.title,
            summary: d.summary ?? '',
            content: d.content,
            categoryId: d.categoryId,
            tagIds: (d.tags ?? []).map((t) => t.id),
            status: d.status,
          };
        },
        error: (err) => {
          // 404 is the expected answer for a draft this role may not read; show it instead of
          // letting the rejection escape unhandled.
          this.error.set(
            err.status === 404
              ? 'That article does not exist, or is not published yet.'
              : (err.error?.message ?? 'Could not load this article.'));
        },
        complete: () => this.settle(),
      });
    }
  }

  save() {
    if (!this.form.title.trim() || !this.form.content.trim()) {
      this.error.set('Title and content are required.');
      return;
    }
    this.busy = true;
    this.error.set('');
    const body = { ...this.form, title: this.form.title.trim(), summary: this.form.summary.trim() || null };
    if (this.id) {
      this.http.put<ArticleDetail>(`${API}/articles/${this.id}`, body).subscribe({
        next: () => this.router.navigate(['/articles']),
        error: (e) => {
          this.busy = false;
          this.error.set(e.error?.message ?? 'Failed to save article.');
        },
      });
    } else {
      this.http.post<ArticleDetail>(`${API}/articles`, body).subscribe({
        next: () => this.router.navigate(['/articles']),
        error: (e) => {
          this.busy = false;
          this.error.set(e.error?.message ?? 'Failed to create article.');
        },
      });
    }
  }

  remove() {
    if (!this.id || !confirm('Delete this article?')) return;
    this.busy = true;
    this.http.delete(`${API}/articles/${this.id}`).subscribe({
      next: () => this.router.navigate(['/articles']),
      error: (e) => {
        this.busy = false;
        this.error.set(e.error?.message ?? 'Failed to delete article.');
      },
    });
  }
}
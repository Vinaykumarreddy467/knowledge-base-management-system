import { Component, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { API, SearchHit } from '../core/api';

@Component({
  selector: 'app-search',
  imports: [FormsModule, ButtonModule, CardModule, InputTextModule, TagModule, MessageModule],
  template: `
    <h2 class="mt-0">Search</h2>
    <p-card>
      <div class="flex gap-2 mb-3">
        <input pInputText [(ngModel)]="q" placeholder="Search the knowledge base" (keyup.enter)="semantic()" class="flex-1" />
        <p-button label="Keyword" severity="secondary" (onClick)="keyword()" [loading]="loading()" />
        <p-button label="Semantic" icon="pi pi-sparkles" (onClick)="semantic()" [loading]="loading()" />
      </div>
      @if (mode()) { <p class="text-sm text-600">{{ hits().length }} result(s) via {{ mode() === 'semantic' ? 'semantic (pgvector)' : 'keyword' }} search</p> }
      @if (error()) { <p-message severity="error" class="mb-2" >{{ error() }}</p-message> }
      @if (mode() === 'semantic' && !error()) {
        <p-message severity="info" class="mb-2">Semantic search uses the local embedding model; only content you are allowed to see is returned.</p-message>
      }
      <div class="flex flex-column gap-3 mt-2">
        @for (hit of hits(); track hit.type + hit.id + hit.chunkIndex) {
          <div class="surface-100 border-round p-3">
            <div class="flex align-items-center gap-2 mb-1">
              <p-tag [value]="hit.type" severity="secondary" />
              <span class="font-medium">{{ hit.title }}</span>
              @if (hit.score !== null) { <span class="text-xs text-600 ml-auto">{{ hit.score?.toFixed(3) }}</span> }
            </div>
            <p class="text-sm m-0 line-height-3">{{ hit.snippet }}</p>
          </div>
        }
      </div>
    </p-card>
  `,
})
export class SearchComponent {
  private http = inject(HttpClient);
  q = '';
  // Signals, not plain fields: this app is zoneless, so results arriving in an
  // HttpClient callback would otherwise never re-render and the view would hang on "loading".
  hits = signal<SearchHit[]>([]);
  mode = signal<'keyword' | 'semantic' | null>(null);
  loading = signal(false);
  error = signal('');

  keyword() { this.run('keyword'); }
  semantic() { this.run('semantic'); }
  private run(mode: 'keyword' | 'semantic') {
    const q = this.q.trim();
    if (!q) { this.error.set('Type something to search for.'); return; }
    this.loading.set(true); this.error.set(''); this.mode.set(mode);
    const url = mode === 'semantic' ? `${API}/search/semantic` : `${API}/search`;
    this.http.get<SearchHit[]>(url, { params: new HttpParams().set('q', q) }).subscribe({
      next: (h) => { this.hits.set(h); this.loading.set(false); },
      error: (e) => { this.loading.set(false); this.error.set(e.error?.message ?? 'Search failed.'); },
    });
  }
}
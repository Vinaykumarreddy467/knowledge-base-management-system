import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { TagModule } from 'primeng/tag';
import { API, Answer, ChatMessage, ChatSession } from '../core/api';

@Component({
  selector: 'app-assistant',
  imports: [DatePipe, FormsModule, ButtonModule, CardModule, InputTextModule, MessageModule, TagModule],
  template: `
    <div class="grid">
      <div class="col-12 md:col-4">
        <p-card header="Sessions">
          <div class="flex flex-column gap-2">
            <p-button label="New session" icon="pi pi-plus" (onClick)="newSession()" [loading]="busy" />
            @if (!sessions().length) { <p class="text-sm text-600">No sessions yet.</p> }
            @for (s of sessions(); track s.id) {
              <div class="flex align-items-center gap-2 surface-100 border-round p-2 clickable" (click)="open(s)">
                <div class="flex-1" style="min-width:0">
                  <div class="text-sm font-medium text-overflow-clip">{{ s.title }}</div>
                  <div class="text-xs text-600">{{ s.updatedAt | date: 'short' }}</div>
                </div>
                <p-button icon="pi pi-trash" text severity="danger" (onClick)="removeSession($event, s)" />
              </div>
            }
          </div>
        </p-card>
      </div>
      <div class="col-12 md:col-8">
        <p-card>
          @if (!providerReady()) {
            <p-message severity="warn" class="mb-2">The language model is not reachable. Answers return a clear error instead of a guess.</p-message>
          }
          <div class="flex flex-column gap-3 mb-3" style="max-height: 55vh; overflow: auto">
            @if (!messages().length) {
              <p class="text-sm text-600">Ask a question. Answers come only from published articles and processed documents, with citations.</p>
            }
            @for (m of messages(); track m.id) {
              <div class="border-round p-3" [style.background]="m.role === 'USER' ? 'var(--p-primary-50)' : 'var(--p-surface-100)'">
                <div class="flex align-items-center gap-2 mb-1">
                  <i class="text-xs" [class]="m.role === 'USER' ? 'pi pi-user' : 'pi pi-sparkles'"></i>
                  <span class="text-xs font-medium text-600">{{ m.role === 'USER' ? 'You' : 'Assistant' }}</span>
                  @if (m.grounding === 'NO_CONTEXT') { <p-tag value="no context" severity="warn" /> }
                </div>
                <div class="article-content text-sm">{{ m.content }}</div>
                @if (m.citations?.length) {
                  <div class="mt-2 pt-2 border-top-1 border-300">
                    <div class="text-xs text-600 mb-1">Sources</div>
                    @for (c of m.citations; track $index) {
                      <div class="text-xs">
                        <i class="pi pi-bookmark text-500 mr-1"></i>{{ c.title }}@if (c.pageNumber) { <span> (p. {{ c.pageNumber }})</span> } &middot; {{ c.sourceType }}#{{ c.sourceId }}
                      </div>
                    }
                  </div>
                }
              </div>
            }
          </div>
          <div class="flex gap-2">
            <input pInputText [ngModel]="question()" (ngModelChange)="question.set($event)" placeholder="Ask about the knowledge base" (keyup.enter)="ask()" class="flex-1" maxlength="2000" />
            <p-button label="Ask" icon="pi pi-send" (onClick)="ask()" [loading]="asking" [disabled]="!question().trim()" />
          </div>
          @if (error()) { <p-message severity="error" class="mt-2" >{{ error() }}</p-message> }
        </p-card>
      </div>
    </div>
  `,
})
export class AssistantComponent implements OnInit {
  private http = inject(HttpClient);
  // Signals, not plain fields: this app is zoneless, so async callbacks that assign
  // plain properties never schedule change detection and the view silently goes stale.
  sessions = signal<ChatSession[]>([]);
  messages = signal<ChatMessage[]>([]);
  current = signal<number | null>(null);
  question = signal('');
  asking = false;
  busy = false;
  error = signal('');
  providerReady = signal(true);

  ngOnInit() { this.loadSessions(); }
  loadSessions() {
    this.http.get<ChatSession[]>(`${API}/ai/sessions`).subscribe({ next: (s) => this.sessions.set(s) });
  }
  newSession() {
    this.busy = true;
    this.http.post<ChatSession>(`${API}/ai/sessions`, { title: 'New session' }).subscribe({
      next: (s) => { this.busy = false; this.sessions.update((list) => [s, ...list]); this.open(s); },
      error: () => (this.busy = false),
    });
  }
  open(s: ChatSession) {
    this.current.set(s.id);
    this.messages.set([]);
    this.http.get<ChatMessage[]>(`${API}/ai/sessions/${s.id}/messages`).subscribe({ next: (m) => this.messages.set(m) });
  }
  removeSession(e: Event, s: ChatSession) {
    e.stopPropagation();
    if (!confirm(`Delete session "${s.title}"?`)) return;
    this.http.delete(`${API}/ai/sessions/${s.id}`).subscribe({
      next: () => {
        if (this.current() === s.id) { this.current.set(null); this.messages.set([]); }
        this.loadSessions();
      },
    });
  }
  ask() {
    const q = this.question().trim();
    if (!q) return;
    this.asking = true; this.error.set('');
    this.question.set('');
    this.http.post<Answer>(`${API}/ai/ask`, { question: q, sessionId: this.current() }).subscribe({
      next: (a) => {
        this.asking = false;
        this.providerReady.set(a.providerConfigured);
        if (a.sessionId) { this.current.set(a.sessionId); }
        this.refreshMessages();
        this.loadSessions();
      },
      error: (e) => { this.asking = false; this.error.set(e.error?.message ?? 'The assistant is unavailable.'); },
    });
  }
  private refreshMessages() {
    const id = this.current();
    if (id == null) return;
    this.http.get<ChatMessage[]>(`${API}/ai/sessions/${id}/messages`).subscribe({ next: (m) => this.messages.set(m) });
  }
}
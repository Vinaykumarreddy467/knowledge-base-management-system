import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArticleEditComponent } from './article-edit';
import { AuthService } from '../core/auth.service';
import { API } from '../core/api';

const ARTICLE = {
  id: 2,
  title: 'Expense policy 130359',
  summary: 'Travel allowance',
  content: 'Employees may claim up to 500 USD for travel.',
  status: 'PUBLISHED' as const,
  categoryId: 1,
  categoryName: 'Onboarding',
  tags: [],
  authorId: 4,
  createdAt: '2026-09-29T07:34:05.156980Z',
  updatedAt: '2026-09-29T07:34:05.156980Z',
  publishedAt: '2026-09-29T07:34:05.163360Z',
};

function roleAuth(role: 'VIEWER' | 'EDITOR' | 'ADMIN') {
  return {
    isStaff: () => role !== 'VIEWER',
    isAdmin: () => role === 'ADMIN',
    user: () => ({ id: 9, email: 'v@kbms.local', fullName: 'V', role }),
  };
}

function routeFor(id: string) {
  return { snapshot: { paramMap: { get: (k: string) => (k === 'id' ? id : null) } } };
}

async function mountFor(id: string, role: 'VIEWER' | 'EDITOR' | 'ADMIN') {
  TestBed.overrideProvider(ActivatedRoute, { useValue: routeFor(id) });
  TestBed.overrideProvider(AuthService, { useValue: roleAuth(role) });
  TestBed.configureTestingModule({
    imports: [ArticleEditComponent],
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(ArticleEditComponent);
  fixture.detectChanges();
  http.expectOne(`${API}/categories/all`).flush([]);
  http.expectOne(`${API}/tags/all`).flush([]);
  return { fixture, http };
}

async function render(fixture: ComponentFixture<ArticleEditComponent>) {
  // No manual detectChanges after the response: the component must schedule its own render.
  await fixture.whenStable();
  fixture.detectChanges();
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}

describe('ArticleEditComponent', () => {
  it('renders published article content that arrived asynchronously (zoneless)', async () => {
    const { fixture, http } = await mountFor('2', 'VIEWER');
    http.expectOne(`${API}/articles/2`).flush(ARTICLE);
    const text = await render(fixture);
    http.verify();
    const el = fixture.nativeElement as HTMLElement;
    // Input/textarea values live on .value, never in textContent.
    const title = el.querySelector('input[maxlength="300"]') as HTMLInputElement | null;
    const content = el.querySelector('textarea[maxlength="500000"]') as HTMLTextAreaElement | null;
    expect(text).toContain('Edit article');
    expect(title?.value).toBe('Expense policy 130359');
    expect(content?.value).toContain('Employees may claim up to 500 USD for travel.');
  });

  it('hides Save and Delete for a VIEWER', async () => {
    const { fixture, http } = await mountFor('2', 'VIEWER');
    http.expectOne(`${API}/articles/2`).flush(ARTICLE);
    const text = await render(fixture);
    http.verify();
    expect(text).not.toContain('Save');
    expect(text).not.toContain('Delete');
  });

  it('shows Save and Delete to an EDITOR', async () => {
    const { fixture, http } = await mountFor('2', 'EDITOR');
    http.expectOne(`${API}/articles/2`).flush(ARTICLE);
    const text = await render(fixture);
    http.verify();
    expect(text).toContain('Save');
    expect(text).toContain('Delete');
  });

  it('surfaces a 404 as a message instead of an unhandled rejection', async () => {
    const { fixture, http } = await mountFor('1', 'VIEWER');
    http.expectOne(`${API}/articles/1`).flush(
      { code: 'NOT_FOUND', message: 'nope', timestamp: '2026-01-01T00:00:00Z' },
      { status: 404, statusText: 'Not Found' },
    );
    const text = await render(fixture);
    http.verify();
    expect(text).toContain('does not exist');
  });
});

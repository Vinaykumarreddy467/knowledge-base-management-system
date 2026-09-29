import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { ShellComponent } from './shell';
import { AuthService } from '../core/auth.service';
import { Role } from '../core/api';

function authFor(role: Role) {
  return {
    user: () => ({ id: 1, email: `${role.toLowerCase()}@kbms.local`, fullName: 'Test User', role }),
    isAdmin: () => role === 'ADMIN',
    isStaff: () => role !== 'VIEWER',
    logout: () => {},
  };
}

async function renderFor(role: Role) {
  TestBed.overrideProvider(AuthService, { useValue: authFor(role) });
  TestBed.configureTestingModule({
    imports: [ShellComponent],
    providers: [provideRouter([])],
  });
  const fixture = TestBed.createComponent(ShellComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  return fixture.nativeElement as HTMLElement;
}

describe('ShellComponent role badge', () => {
  it.each([
    ['ADMIN', 'ADMIN'],
    ['EDITOR', 'EDITOR'],
    ['VIEWER', 'VIEWER'],
  ] as const)('shows the %s badge for an authenticated %s', async (role, expected) => {
    const el = await renderFor(role);
    expect(el.textContent).toContain(expected);
    // The badge must be labelled for assistive tech, not colour-only.
    expect(el.querySelector('[aria-label="Role: ' + expected + '"]')).toBeTruthy();
  });

  it('does not hard-code a role: each account renders its own', async () => {
    const viewer = await renderFor('VIEWER');
    expect(viewer.textContent).toContain('VIEWER');
    expect(viewer.textContent).not.toContain('ADMIN');
  });

  it('renders no badge when there is no authenticated user', async () => {
    TestBed.overrideProvider(AuthService, {
      useValue: { user: () => null, isAdmin: () => false, isStaff: () => false, logout: () => {} },
    });
    TestBed.configureTestingModule({ imports: [ShellComponent], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[aria-label^="Role:"]')).toBeNull();
  });

  it('maps roles to distinct severities', () => {
    TestBed.overrideProvider(AuthService, { useValue: authFor('ADMIN') });
    TestBed.configureTestingModule({ imports: [ShellComponent], providers: [provideRouter([])] });
    const cmp = TestBed.createComponent(ShellComponent).componentInstance;
    expect(cmp.roleSeverity('ADMIN')).toBe('danger');
    expect(cmp.roleSeverity('EDITOR')).toBe('warn');
    expect(cmp.roleSeverity('VIEWER')).toBe('secondary');
  });
});

import { Routes } from '@angular/router';
import { adminGuard, authGuard } from './core/guards';
import { ShellComponent } from './layout/shell';
import { LoginComponent } from './features/login';
import { DashboardComponent } from './features/dashboard';
import { ArticleListComponent } from './features/articles';
import { ArticleEditComponent } from './features/article-edit';
import { CategoriesComponent } from './features/categories';
import { TagsComponent } from './features/tags';
import { DocumentsComponent } from './features/documents';
import { SearchComponent } from './features/search';
import { AssistantComponent } from './features/assistant';
import { UsersComponent } from './features/users';
import { AuditComponent } from './features/audit';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', component: DashboardComponent },
      { path: 'articles', component: ArticleListComponent },
      { path: 'articles/new', component: ArticleEditComponent },
      { path: 'articles/:id', component: ArticleEditComponent },
      { path: 'categories', component: CategoriesComponent, canActivate: [adminGuard] },
      { path: 'tags', component: TagsComponent, canActivate: [adminGuard] },
      { path: 'documents', component: DocumentsComponent },
      { path: 'search', component: SearchComponent },
      { path: 'assistant', component: AssistantComponent },
      { path: 'admin/users', component: UsersComponent, canActivate: [adminGuard] },
      { path: 'admin/audit', component: AuditComponent, canActivate: [adminGuard] },
    ],
  },
  { path: '**', redirectTo: '' },
];
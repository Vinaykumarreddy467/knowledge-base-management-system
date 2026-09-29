export const API = 'http://localhost:8080/api';

export type Role = 'ADMIN' | 'EDITOR' | 'VIEWER';
export type ArticleStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'PROCESSED' | 'FAILED';

export interface UserView {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  active: boolean;
  createdAt: string;
}

export interface LoginResponse {
  accessToken: string;
  expiresInSeconds: number;
  user: UserView;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface Category {
  id: number;
  name: string;
  description: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Tag {
  id: number;
  name: string;
  createdAt: string;
}

export interface ArticleSummary {
  id: number;
  title: string;
  summary: string;
  status: ArticleStatus;
  categoryId: number | null;
  categoryName: string | null;
  authorId: number;
  updatedAt: string;
}

export interface ArticleDetail {
  id: number;
  title: string;
  summary: string;
  content: string;
  status: ArticleStatus;
  categoryId: number | null;
  categoryName: string | null;
  tags: Tag[];
  authorId: number;
  updatedBy: number | null;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
}

export interface ArticleUpsert {
  title: string;
  summary: string;
  content: string;
  categoryId: number | null;
  tagIds: number[];
  status: ArticleStatus;
}

export interface DocumentResponse {
  id: number;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  status: DocumentStatus;
  failureReason: string | null;
  chunkCount: number;
  uploadedBy: number;
  uploadedAt: string;
  processedAt: string | null;
}

export interface SearchHit {
  type: string;
  id: number;
  title: string;
  snippet: string;
  status: string;
  fileType: string | null;
  score: number | null;
  chunkIndex: number | null;
}

export interface Citation {
  sourceType: string;
  sourceId: number;
  title: string;
  chunkIndex: number | null;
  pageNumber: number | null;
  section: string | null;
  score: number;
}

export interface Answer {
  sessionId: number | null;
  answer: string;
  citations: Citation[];
  grounding: 'ANSWERED' | 'NO_CONTEXT';
  model: string;
  providerConfigured: boolean;
}

export interface ChatSession {
  id: number;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: number;
  role: 'USER' | 'ASSISTANT';
  content: string;
  citations: Citation[];
  grounding: 'ANSWERED' | 'NO_CONTEXT';
  createdAt: string;
}

export interface RecentDocument {
  id: number;
  name: string;
  status: string;
  uploadedAt: string;
}

export interface Dashboard {
  articles: number;
  publishedArticles: number;
  draftArticles: number;
  documents: number;
  processedDocuments: number;
  failedDocuments: number;
  processingDocuments: number;
  categories: number;
  tags: number;
  aiQuestions: number;
  recentDocuments: RecentDocument[];
}

export interface AuditView {
  id: number;
  actorEmail: string;
  action: string;
  entityType: string;
  entityId: string;
  detail: string;
  createdAt: string;
}
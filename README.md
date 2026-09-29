# KBMS

A knowledge base management system: articles, uploaded documents, semantic search, and a
grounded AI assistant that answers only from retrieved content and cites the exact chunks it used.

- **Backend** — Spring Boot 3.5.15, Java 21, PostgreSQL 17 + pgvector, Flyway, JWT (jjwt), Apache Tika
- **Frontend** — Angular 22, PrimeNG 22, standalone components, zoneless change detection
- **AI** — local [Ollama](https://ollama.com) by default: `nomic-embed-text-v2-moe` for embeddings
  and `gemma:2b` for answers. No API key, no data leaving the machine.

> This document describes the code as it stands in this working copy. Every version, test count and
> status below was read from the project files or observed on a running stack on the date of writing.
> Anything not directly verified is labelled as such rather than presented as a fact.

---

## 1. Project overview

KBMS stores curated **articles** and uploaded **documents**, makes both searchable with keyword and
vector search, and exposes a **grounded assistant** that answers only from retrieved content and
attaches citations built from the retrieved database rows.

**MVP scope, as implemented:**

| Area | Status |
| --- | --- |
| Articles: CRUD, draft/publish/archive transitions, category + tag links | Implemented |
| Documents: multipart upload, Tika extraction, chunking, embedding, reprocess, delete | Implemented |
| Keyword search (PostgreSQL full-text) and semantic search (pgvector) | Implemented |
| Grounded AI chat with citations, per-user session history | Implemented |
| Roles and authorization (ADMIN / EDITOR / VIEWER) | Implemented, backend-enforced |
| User administration and audit log | Implemented (ADMIN only) |
| Angular UI for all of the above | Implemented, all 9 screens render with data |

**Deliberately out of scope** (stated in the migration and not built): OCR, multi-tenancy, reranking,
hybrid search, AI article generation, and a vector index (IVFFlat/HNSW).

**Version control note.** This working copy is **not a Git repository** — there is no `.git`
directory at the project root or under `backend/`. No commit history or diff is available, so
nothing in this report is derived from Git.

## 2. Technology stack

Versions read from `frontend/package.json`, `backend/pom.xml`, `docker-compose.yml` and
`application.yml`; runtime values confirmed against the running services.

### Frontend

| Package | Declared | Notes |
| --- | --- | --- |
| `@angular/*` (core, common, router, forms, platform-browser) | `^22.2.0` | Standalone components |
| `@angular/animations` | `^22.2.0` | Required by PrimeNG; added during setup |
| `primeng` | `^22.1.1` | Resolved to 22.1.5 in `node_modules` |
| `@primeuix/themes` | `^3.0.1` | Aura preset |
| `primeicons` / `primeflex` | `^8.0.2` / `^4.0.0` | Icon set and utility grid |
| `typescript` | `~6.0.2` | |
| `vitest` | `^5.0.0` | Runner behind `ng test` (`@angular/build:unit-test`) |

### Backend

| Component | Version | Notes |
| --- | --- | --- |
| Java | 21 | `mvn` only; no Gradle |
| Spring Boot | 3.5.15 | web, data-jpa, security, validation, actuator |
| jjwt | 0.12.6 | JWT issue/verify |
| Apache Tika | 3.3.2 | Text extraction (`tika-core`, `tika-parsers`) |
| Flyway | via Boot BOM | `flyway-core` + `flyway-database-postgresql` |

### Database

| Component | Value | Notes |
| --- | --- | --- |
| Image | `pgvector/pgvector:pg17` | PostgreSQL 17 with the `vector` extension |
| Container | `kbms-db` | Published on **55432** by default (5432 is usually taken by a native install) |
| Extensions | `vector`, `pg_trgm` | Created by the migration |
| Embedding column | `document_chunks.embedding vector(${vector_dimension})` | Flyway placeholder |

### AI integration

**There is no Spring AI dependency in this project.** Ollama is called directly through Spring's
`RestClient`; see `config/OllamaClientConfig`, which builds two qualified clients
(`ollamaEmbeddingRestClient`, `ollamaLlmRestClient`) so a broken LLM URL cannot silently shadow a
healthy embedding URL.

| Setting | Default | Verified |
| --- | --- | --- |
| Ollama version | — | `0.34.4` (from `/api/version`) |
| Embedding model | `nomic-embed-text-v2-moe:latest` | Installed, 913 MB |
| LLM model | `gemma:2b` | Installed, 1601 MB |
| Embedding dimension | `768` | **Confirmed from the live model** — `POST /api/embeddings` returned 768 floats |

The 768 figure is not assumed. `OllamaReadinessCheck.verifyDimension()` embeds a probe string at
startup and compares the real width with `KBMS_VECTOR_DIMENSION`, logging an error on mismatch;
`OllamaEmbeddingService` additionally refuses to return a vector whose length disagrees, so a wrong
width cannot silently corrupt the pgvector column.

Embedding and LLM providers are selectable via `KBMS_EMBEDDING_PROVIDER` and `KBMS_LLM_PROVIDER`:

| Value | Embedding | LLM |
| --- | --- | --- |
| `ollama` (default) | `OllamaEmbeddingService` | `OllamaChatLlmService` |
| `local` | `LocalHashEmbeddingService` (deterministic, no model) | `UnconfiguredLlmService` |
| `openai` | `OpenAiEmbeddingService` | `OpenAiChatLlmService` |

## 3. Repository structure

```
KBMS/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/kbms/
│       │   │   ├── KbmsApplication.java
│       │   │   ├── ai/            ChatController, RagService, EmbeddingService/LlmService
│       │   │   │                   providers (Ollama / OpenAI / local / unconfigured),
│       │   │   │                   OllamaReadinessCheck
│       │   │   ├── audit/          AuditController, AuditEvent, AuditRepository, AuditService
│       │   │   ├── chat/           ChatSession, ChatMessage + repositories
│       │   │   ├── common/         ApiError, ApiException, GlobalExceptionHandler, PageResponse
│       │   │   ├── config/         SecurityConfig, AuthConfig, OllamaClientConfig,
│       │   │   │                   IndexingConfig, AppProperties
│       │   │   ├── dashboard/      DashboardController, DashboardService
│       │   │   ├── document/       DocumentController, DocumentService, DocumentProcessor,
│       │   │   │                   DocumentStorage, TextExtractor, FileType, DocumentStatus,
│       │   │   │                   KbDocument, DocumentChunk + repositories, SourceType
│       │   │   ├── index/          ContentIndexer, TextChunker, TextCleanup
│       │   │   ├── search/         SearchController, SemanticSearchService
│       │   │   ├── security/       AuthController, JwtService, JwtAuthFilter, CurrentUser
│       │   │   ├── taxonomy/       Article/Category/Tag controllers, services, repositories,
│       │   │   │                   ArticleSpecs (visibility rules), Texts, enums
│       │   │   └── user/           UserController, UserService, AppUser, Role, repository
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/V1__init.sql
│       └── test/java/com/kbms/
│           ├── ai/OllamaProvidersTest.java, RagServiceTest.java
│           ├── dashboard/DashboardServiceCountTest.java
│           └── document/DocumentUploadApiTest.java
├── frontend/
│   ├── package.json, angular.json
│   └── src/
│       ├── main.ts, index.html, styles.scss
│       └── app/
│           ├── app.ts, app.config.ts, app.routes.ts, app.spec.ts
│           ├── core/       api.ts (DTOs + API base), auth.service.ts,
│           │               auth.interceptor.ts, guards.ts
│           ├── layout/     shell.ts (header, nav, role badge), shell.spec.ts
│           └── features/   login, dashboard, articles, article-edit, categories, tags,
│                           documents, search, assistant, users, audit
├── docker-compose.yml
├── .env.example
└── README.md
```

### Database schema (`V1__init.sql`, 10 tables)

`app_users`, `categories`, `tags`, `knowledge_articles`, `article_tags`, `documents`,
`document_chunks`, `chat_sessions`, `chat_messages`, `audit_events`.

Notable schema details:

- Category and tag names are unique **case-insensitively** (`UNIQUE (lower(name))`).
- `knowledge_articles` carries a generated `search_vector tsvector` with weights
  A=title, B=summary, C=content, indexed with GIN; a `pg_trgm` GIN index backs title substring search.
- `document_chunks.embedding` is `vector(${vector_dimension})` — the Flyway placeholder.
- `document_chunks` is **deliberately not** using an IVFFlat/HNSW index; the migration says so and
  advises adding one with pgvector >= 0.5 once the corpus justifies the build cost.
- Article tags cascade on article or tag delete; chat messages cascade on session delete.

## 4. Architecture and data flow

**Frontend → API.** Standalone Angular components talk to `API` in `core/api.ts`
(`http://localhost:8080/api`, hard-coded absolute). `authInterceptor` attaches
`Authorization: Bearer <token>` and on `401` clears the session and routes to `/login`.
`authGuard` blocks unauthenticated routes; `adminGuard` blocks admin routes. Cross-origin is handled
by backend CORS, default `http://localhost:4200` (`KBMS_CORS_ORIGINS`). There is no dev proxy.

**Authentication.** `POST /api/auth/login` verifies the BCrypt hash, issues a signed JWT
(`jjwt`), and returns the user record. `JwtAuthFilter` resolves the token on each request;
`AuthConfig` loads **only active users**. Tokens are access-only — there is no refresh or revocation.
The first administrator is bootstrapped only when the user table is empty and both
`KBMS_ADMIN_EMAIL` and `KBMS_ADMIN_PASSWORD` are set; there is no self-registration.

**Articles.** Writes are restricted to ADMIN/EDITOR. Reads are filtered **in SQL** through
`ArticleSpecs.visibleTo(staff)` and re-checked in `ArticleService`, so a VIEWER only ever receives
PUBLISHED rows — from listings, detail reads, keyword search and vector retrieval alike. The same
rule covers ARCHIVED, which staff can read and viewers cannot.

**Documents.** Multipart upload (ADMIN/EDITOR) stores the file on the local filesystem, then
`DocumentProcessor` runs asynchronously: Tika extracts text, `TextChunker` splits it
(`KBMS_CHUNK_CHARS` 1200, `KBMS_CHUNK_OVERLAP` 150), `ContentIndexer` embeds each chunk and writes it
to `document_chunks`. Reprocessing replaces the chunk set; deleting a document cascades to its
chunks. Lifecycle is UPLOADED → PROCESSING → PROCESSED / FAILED, with the failure reason surfaced.

**Extraction and embeddings.** `EmbeddingService` is a provider boundary (`OllamaEmbeddingService`,
`OpenAiEmbeddingService`, or the deterministic `LocalHashEmbeddingService`). Because the dimension is
a deliberate, configured choice, the service refuses any vector whose length disagrees with
configuration rather than writing a corrupt value.

**Search.** Keyword search uses the weighted `tsvector` and applies visibility in the same SQL
statement. Semantic search embeds the query with the same model that embedded the chunks, then calls
`DocumentChunkRepository.findNearestChunks`, whose native query enforces, in SQL, that non-staff may
only match chunks from PUBLISHED articles or PROCESSED documents, and applies the cosine-distance
floor.

**RAG answer generation.** `RagService` embeds the question, retrieves authorized chunks, drops
anything below `KBMS_RAG_MIN_SIMILARITY` (default 0.35), and **does not call the model at all** when
nothing survives — it returns the insufficiency message instead. The model receives only the
surviving chunks, delimited and labelled as untrusted quoted data. **Citations are assembled by the
application from the retrieved rows, never parsed from model text**, so a fabricated source cannot
appear. A model refusal is normalised to the same insufficiency message rather than being displayed
alongside weak citations. Answers persist with `grounding` = `ANSWERED` or `NO_CONTEXT`.

**Chat history.** Sessions are owned by a user. `requireOwnedSession` guards every read and delete,
and a non-owner receives the **same `404`** as a nonexistent id, so a guessed id reveals nothing.

**Audit.** Writes to articles, documents, users, taxonomy and authentication are recorded by
`AuditService` with actor, action, entity and timestamp. The audit log is readable by ADMIN only.
Observed rows contained file names and entity ids but no tokens, passwords or document bodies.

*Partially verified:* the asynchronous `DocumentProcessor` path, Tika extraction and embedding writes
were observed through the running stack (a document reached `PROCESSED` with a non-zero chunk count),
but the failure branches of `TextExtractor` and `ContentIndexer` were not exercised.

## 5. Role and access matrix

Three roles exist — `ADMIN`, `EDITOR`, `VIEWER` (`user/Role.java`). `Role.isStaff()` is true for
ADMIN and EDITOR. There is no self-service registration: an ADMIN creates every account with an
explicit role, and the first admin is bootstrapped from the environment.

**"UI" is what the browser shows. "Backend" is the observed HTTP result.** A hidden button is never
treated as proof of protection.

| Action | VIEWER | EDITOR | ADMIN | Backend enforcement |
| --- | --- | --- | --- | --- |
| View dashboard | UI yes | UI yes | UI yes | none — any authenticated user |
| View published articles | UI yes | UI yes | UI yes | `ArticleSpecs.visibleTo` |
| View **draft** articles | UI no | UI yes | UI yes | **404** for VIEWER (verified) |
| View **archived** articles | UI no | UI yes | UI yes | Code hides; **not verified** (no archived row) |
| View categories / tags (read) | UI no (route) | UI no (route) | UI yes | No rule — any authenticated user |
| View documents and extracted text | UI yes | UI yes | UI yes | No rule — any authenticated user |
| Create article | UI no | UI yes | UI yes | `@PreAuthorize` ADMIN/EDITOR — 403 verified |
| Edit / publish / archive article | UI no | UI yes | UI yes | `@PreAuthorize` ADMIN/EDITOR — 403 verified |
| Delete article | UI no | UI yes | UI yes | `@PreAuthorize` ADMIN/EDITOR — 403 verified |
| Create / edit / delete category | UI no (route) | UI no (route) | UI yes | `hasRole('ADMIN')` — 403 verified |
| Create / edit / delete tag | UI no (route) | UI no (route) | UI yes | `hasRole('ADMIN')` — 403 verified |
| Upload document | UI disabled | UI yes | UI yes | ADMIN/EDITOR — 403 verified |
| Reprocess / delete document | UI no | UI yes | UI yes | ADMIN/EDITOR — 403 verified |
| Keyword search | UI yes | UI yes | UI yes | visibility filtered in SQL |
| Semantic search | UI yes | UI yes | UI yes | visibility filtered in SQL |
| AI chat and citations | UI yes | UI yes | UI yes | authorized context only |
| Own chat history | UI yes | UI yes | UI yes | ownership-scoped |
| **Another user's** chat session | UI no | UI no | UI own | **404** for non-owner (verified) |
| Manage users / change roles | UI no (route) | UI no (route) | UI yes | `hasRole('ADMIN')` — 403 verified |
| View audit log | UI no (route) | UI no (route) | UI yes | `hasRole('ADMIN')` — 403 verified |

The last active administrator cannot be demoted, deactivated or deleted (`UserService.protectLastAdmin`).
The guard was **not** exercised, because verifying it requires a mutation.

### Unresolved permission and UI findings

1. **Document read endpoints are not filtered.** `GET /api/documents`, `/api/documents/{id}` and
   `/api/documents/{id}/content` carry no `@PreAuthorize` and no status predicate, while vector
   retrieval admits only `PROCESSED` documents. Any authenticated role can therefore read a
   `FAILED`, `PROCESSING` or `UPLOADED` document by id. This is an asymmetry with retrieval.
   **Not verifiable with the current data** — all five documents are `PROCESSED`, so no negative case
   exists. Needs a product decision on whether non-processed documents should be hidden.
2. **Category and tag reads are open to all roles.** The management screens are ADMIN-only (route
   guard plus `@PreAuthorize`), but `GET /api/categories` and `GET /api/tags` are readable by any
   authenticated user, including VIEWER.
3. **Dashboard document counters are not role-filtered.** Article counters were fixed during this
   work (see §8), but `documents`, `processedDocuments`, `failedDocuments` and `processingDocuments`
   are global counts shown to every role.

## 6. API surface

Derived from the controllers in the current source, not from an original specification.

| Group | Routes | Auth |
| --- | --- | --- |
| Auth | `POST /api/auth/login`, `GET /api/auth/me` | login is public |
| Dashboard | `GET /api/dashboard` | any authenticated |
| Articles | `GET /api/articles`, `GET /api/articles/{id}`, `POST`, `PUT /{id}`, `POST /{id}/status/{status}`, `DELETE /{id}` | reads any authenticated; writes ADMIN/EDITOR |
| Categories | `GET`, `GET /all`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` | reads any authenticated; writes ADMIN |
| Tags | `GET`, `GET /all`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` | reads any authenticated; writes ADMIN |
| Documents | `GET`, `GET /{id}`, `GET /{id}/content`, `POST` (multipart), `POST /{id}/process`, `DELETE /{id}` | reads any authenticated; writes ADMIN/EDITOR |
| Search | `GET /api/search?q=`, `GET /api/search/semantic?q=` | any authenticated |
| Assistant | `POST /api/ai/ask`, `GET /api/ai/sessions`, `POST /api/ai/sessions`, `GET /api/ai/sessions/{id}`, `GET /api/ai/sessions/{id}/messages`, `DELETE /api/ai/sessions/{id}` | any authenticated; sessions ownership-scoped |
| Admin users | `GET /api/admin/users`, `POST`, `PUT /{id}`, `DELETE /{id}` | ADMIN (class-level) |
| Admin audit | `GET /api/admin/audit` | ADMIN (class-level) |

Send `Authorization: Bearer <accessToken>`. Errors use one envelope:
`{"code","message","timestamp"}` — for example `VALIDATION_FAILED`, `FORBIDDEN`, `NOT_FOUND`,
`PROVIDER_UNAVAILABLE`, `INTERNAL_ERROR`. List endpoints return
`{items, page, size, totalItems, totalPages}`.

`GET /api/ai/sessions/{id}` was added during this work; see §8.

## 7. Testing performed

Everything below was executed in this working copy. "Read-only" means the check created, edited,
uploaded or deleted no application record.

### Automated test suites (current run)

| Layer | Command | Result | Notes |
| --- | --- | --- | --- |
| Backend | `mvn test` (in `backend/`) | **27 passed, 0 failed, 0 errors, 0 skipped** | `OllamaProvidersTest` 9, `RagServiceTest` 10, `DashboardServiceCountTest` 2, `DocumentUploadApiTest` 6 |
| Frontend | `npm test` (in `frontend/`) | **12 passed, 3 files** | vitest via `@angular/build:unit-test` |
| Frontend build | `npm run build` | success | 1.71 MB raw, ~269 kB estimated transfer |

The backend suite mocks the HTTP boundary to Ollama, so it needs no model, no database and no
network. `DocumentUploadApiTest` is a `@WebMvcTest` slice with mocked services, real security
filter chain and real `@PreAuthorize` evaluation.

### API smoke checks

An API smoke script (kept outside the repo, in the temp working directory) exercised the live
stack against Docker Postgres and the real backend. In the most recent full run it reported
**50 passed / 0 failed**, idempotently. It creates its own timestamped fixtures and is therefore
**not read-only**; it was not re-run while preparing this report, to avoid adding fixture rows.

### Playwright browser checks (real browser, real backend, real model)

Performed with the Playwright MCP against `http://localhost:4200` with the backend on 8080. All
navigation, filtering and citation checks were **read-only**. The only writes were a disposable
article and a disposable document, both deleted at the end of the pass.

| Check | Role | Result | Evidence |
| --- | --- | --- | --- |
| Login and shell render | all three | Pass | Title `KBMS`, nav links render, 0 console errors |
| Role badge shows the authenticated role | ADMIN / EDITOR / VIEWER | Pass | `ADMIN` (p-tag-danger), `EDITOR` (p-tag-warn), `VIEWER` (p-tag-secondary); each account showed only its own role |
| Small-screen header | VIEWER, ADMIN | Pass after fix | At 475 px the header no longer overflows; nav scrolls internally; badge stays in viewport |
| Dashboard tiles | all three | Pass | VIEWER `articles=3 drafts=0`; EDITOR and ADMIN `6/3/3` |
| Articles list | VIEWER | Pass | 3 rows, all PUBLISHED; no drafts; no "New article" button |
| Draft by direct id | VIEWER | Pass | `GET /api/articles/1` → **404** |
| Published article detail | VIEWER | Pass | Title, content and category render; only `Cancel` shown (no Save/Delete) |
| Draft detail | VIEWER | Pass | "That article does not exist, or is not published yet."; no unhandled console error |
| Same detail as EDITOR | EDITOR | Pass | Title renders; `Delete`, `Cancel`, `Save` present |
| Documents list | VIEWER | Pass | 5 rows, Upload button disabled, viewer notice shown |
| Categories / tags by direct URL | VIEWER | Pass | Both redirect to `/` |
| Categories as ADMIN | ADMIN | Pass | Reachable, "New category" enabled |
| Admin routes by direct URL | VIEWER, EDITOR | Pass | `/admin/users` and `/admin/audit` redirect to `/` |
| Admin APIs | VIEWER, EDITOR | Pass | `GET /api/admin/users`, `/api/admin/audit` → **403** |
| Viewer mutations | VIEWER | Pass | 7 distinct mutations (article create/edit, category create/update, tag create, document upload, admin user create) → **403** |
| Keyword search, paraphrase | VIEWER | Pass | `GET /api/search?q=Draft-` → 0 results for viewer; 3 for EDITOR/ADMIN |
| Keyword search | EDITOR, ADMIN | Pass | `q=refund` → 3 documents |
| Semantic search, paraphrase | VIEWER | Pass | 3 `DOCUMENT` hits, score 0.546, from `/api/search/semantic` |
| Semantic search, same query as keyword | VIEWER | Pass | Query `How long does it take to get my money back?` → **0 keyword / 3 semantic** |
| AI chat with citations | VIEWER | Pass | Answer rendered with `refund-130359.txt · DOCUMENT#1`, `#2`, `#3` |
| Insufficiency response | ADMIN | Pass | Unrelated question returned "no context" and the insufficiency message, with no invented sources |
| Cross-user session | VIEWER, EDITOR | Pass | `GET /api/ai/sessions/2` → **404**; own session 4 → 200 |
| Foreign vs missing session | VIEWER | Pass | Identical `NOT_FOUND` body apart from the id |

**Semantic vs keyword evidence.** The indexed document reads *"Refunds are issued within 14 days of
purchase. Bring the original receipt to the finance desk. Late refunds are escalated to the finance
manager."* The query *"How long does it take to get my money back?"* shares no content words with it.
Keyword search returned **0** results; semantic search returned **3** ranked hits. The browser
network log shows the two distinct endpoints, `GET /api/search?q=…` and
`GET /api/search/semantic?q=…`, each returning 200, so the semantic path is proven rather than
inferred from a label.

**RAG grounding evidence.** `POST /api/ai/ask` returned `grounding: "ANSWERED"` with two citations
resolved to real records. At the time of that check those were `DOCUMENT#38` (the disposable uploaded
document) and `ARTICLE#7` (the disposable article); **both were deleted at the end of that pass**, so
they are cited here as evidence of the mechanism, not as records that still exist. The refund-document
citations verified for the VIEWER in the table above are still present. A deliberately unrelated
question returned the "no context" badge and the insufficiency message with no sources.

**Audit evidence.** The audit screen rendered 51 rows including `DOCUMENT_UPLOAD`,
`DOCUMENT_PROCESS` and `AUTH_LOGIN` entries for the test run. No tokens, passwords or document bodies
appeared in the detail column.

### Not tested

| Item | Why |
| --- | --- |
| A VIEWER asking a *new* AI question | Writing a chat message was out of scope for the read-only passes; citation rendering and own-history reads were verified instead |
| Inaccessible-document hiding | No non-`PROCESSED` document exists in the data |
| Archived-article visibility for VIEWER | No `ARCHIVED` article exists; the code path was read but not exercised |
| Last-active-admin guard | Requires demoting or deleting a user |
| Full smoke script re-run while writing this report | It creates timestamped fixtures; not re-run to avoid polluting the data |

## 8. Fixes made during implementation and testing

Each root cause below was confirmed in the current source.

1. **Zoneless change detection.** The app has no `provideZoneChangeDetection`, so Angular 22 runs it
   zoneless. Plain properties assigned inside `HttpClient` callbacks never schedule a render, which is
   why tables and transcripts silently stayed empty. Converted async state to signals across
   `documents`, `articles`, `dashboard`, `categories`, `tags`, `users`, `audit`, `search` and
   `assistant`. In `article-edit` the fields are bound with `ngModel`, which cannot two-way bind to a
   signal call, so a counter-backed `loaded` signal read by the template schedules the render instead.
2. **Deprecated PrimeNG templates.** `PrimeTemplate` is marked *"@deprecated Use ng-template
   #templateName instead"* in PrimeNG 22, and `pTable` resolves templates by reference name
   (`contentChild('body')`). `pTemplate="body"` was therefore never matched and rows rendered as empty
   comment placeholders. Converted `pTemplate="header|body|emptydata|footer"` to
   `#header|#body|#emptydata|#footer`.
3. **Search screen.** Same zoneless cause: results never rendered and the view hung with both buttons
   disabled. Converted `hits`, `mode`, `loading` and `error` to signals.
4. **Assistant.** Same cause: sessions loaded but the transcript and citations never appeared.
   Converted `sessions`, `messages`, `current`, `question`, `error` and `providerReady` to signals.
5. **Article detail.** Same cause plus a missing `error` callback, which surfaced a draft `404` as an
   unhandled console error. Added a counter-backed `loaded` signal, an `error` signal, a friendly 404
   message, and a loading progress bar.
6. **Missing taxonomy navigation and guards.** `Categories` and `Tags` had routes but no nav links, and
   the routes carried no guard, so a viewer could open the management screens directly. Added
   admin-only nav links and `adminGuard` on both routes. The backend `@PreAuthorize` was already
   correct and was not weakened. The articles list "New article" button was also gated on
   `isStaff()`, the last ungated write control.
7. **Malformed document requests.** `POST /api/documents` with a missing part, a wrong part name or a
   non-multipart `Content-Type` threw `MissingServletRequestPartException` /
   `HttpMediaTypeNotSupportedException`, which were unhandled and fell through to the catch-all as
   **500**. Both now map to **400** with the standard envelope. The viewer **403** for an unauthorized
   upload is unchanged and locked by a test.
8. **Viewer dashboard counts.** `DashboardService.get(staff)` ignored `staff` for every counter, so a
   viewer saw `articles=6` including three drafts while the list correctly showed three. Added
   `ArticleRepository.countVisible(staff)` and made the draft counter zero for non-staff. Staff
   counters are unchanged.
9. **Non-owner session lookup.** `GET /api/ai/sessions/{id}` was not a defined route and returned
   **500**. Added `RagService.session(long)` reusing the same `requireOwnedSession` guard as the
   messages and delete routes, so a non-owner and a nonexistent id both return an identical `404`.
10. **Role badge.** A `p-tag` beside the user name rendering `auth.user()?.role` with an
    `aria-label`, severity per role and a tooltip. Display only — it is not used for access control.
11. **Small-screen header overflow.** At a 475 px viewport the header overflowed and the role badge
    was pushed outside the viewport, because the nav did not shrink. The nav now shrinks and scrolls
    internally (`min-w-0 overflow-x-auto`) with `whitespace-nowrap` links.
12. **Paginators.** Several components bound `[first]="pageIndex"`; PrimeNG expects a row offset, so
    this is now `[first]="pageIndex * size"`.

## 9. Run and configuration guide

### Prerequisites

| Tool | Version used | Notes |
| --- | --- | --- |
| Java | 21 | `mvn` only; no Gradle |
| Node.js | 24 (v24.19.0 observed) with npm 11 | Angular CLI 22 |
| Docker | 29 (29.8.0 observed) | runs PostgreSQL 17 + pgvector |
| Ollama | 0.34+ (0.34.4 observed) | local embeddings and answers |

A native PostgreSQL install usually owns port 5432, so compose publishes the database on **55432**.
Override with `DB_PORT`.

### Environment variables

Placeholders only — no real credential belongs in this file. Every setting and its default is listed
in [`.env.example`](.env.example).

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Backend HTTP port |
| `DB_URL` | `jdbc:postgresql://localhost:55432/kbms` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` / `DB_NAME` | `kbms` | Database credentials (set your own) |
| `DB_PORT` | `55432` | Host port published by compose |
| `KBMS_JWT_SECRET` | none in production use | JWT signing key, at least 32 characters |
| `KBMS_JWT_TTL_MINUTES` | `480` | Access-token lifetime |
| `KBMS_ADMIN_EMAIL` / `KBMS_ADMIN_PASSWORD` | none | First admin, created only while the user table is empty |
| `KBMS_CORS_ORIGINS` | `http://localhost:4200` | Allowed frontend origin |
| `KBMS_STORAGE_DIR` | `./data/uploads` | Uploaded file storage |
| `KBMS_MAX_UPLOAD_SIZE` | `20MB` | Upload limit |
| `KBMS_CHUNK_CHARS` / `KBMS_CHUNK_OVERLAP` | `1200` / `150` | Chunking window and overlap |
| `KBMS_VECTOR_DIMENSION` | `768` | Must equal the model's real width |
| `KBMS_EMBEDDING_PROVIDER` | `ollama` | `ollama`, `local`, or `openai` |
| `KBMS_EMBEDDING_MODEL` | `nomic-embed-text-v2-moe:latest` | Embedding model |
| `KBMS_EMBEDDING_BASE_URL` | `http://localhost:11434` | Ollama embeddings URL |
| `KBMS_LLM_PROVIDER` | `ollama` | `ollama`, `local`, or `openai` |
| `KBMS_LLM_MODEL` | `gemma:2b` | Answer model |
| `KBMS_LLM_BASE_URL` | `http://localhost:11434` | Ollama chat URL |
| `KBMS_RAG_TOP_K` | `5` | Chunks retrieved per question |
| `KBMS_RAG_MIN_SIMILARITY` | `0.35` | Cosine-distance floor |
| `KBMS_RAG_MAX_QUESTION` | `2000` | Question length cap |
| `KBMS_RAG_MAX_CONTEXT` | `24000` | Context character cap |

### Database and pgvector

```bash
docker compose up -d          # starts kbms-db (pgvector/pgvector:pg17) on 55432
```

Flyway applies `V1__init.sql` on first start, creating the `vector` and `pg_trgm` extensions and all
ten tables.

### Ollama

Ollama must be running before the backend starts.

```bash
ollama serve                      # if not already a service
ollama pull nomic-embed-text-v2-moe:latest
ollama pull gemma:2b
ollama list                       # both models must appear
```

Confirm the endpoint answers: `curl http://localhost:11434/api/version`.

Confirm the embedding width before trusting the default:

```bash
curl -s http://localhost:11434/api/embeddings \
  -d '{"model":"nomic-embed-text-v2-moe:latest","prompt":"dimension check"}'
# the "embedding" array must have 768 entries
```

To change width, set `KBMS_VECTOR_DIMENSION` to the model's real width **and** add a migration —
old vectors live in the old space and will not match new queries:

```sql
ALTER TABLE document_chunks ALTER COLUMN embedding TYPE vector(N);
```

When Ollama is unavailable there are **no silent fallbacks**: the backend logs the real reason at
startup and the assistant returns `503 PROVIDER_UNAVAILABLE` naming the URL or model. Everything
else — articles, documents, search, admin — keeps working.

### Backend and frontend

```bash
# 1. database
docker compose up -d

# 2. backend
cd backend
export KBMS_JWT_SECRET="<at-least-32-characters>"
export KBMS_ADMIN_EMAIL="<admin@example.com>"
export KBMS_ADMIN_PASSWORD="<a-strong-password>"
mvn spring-boot:run             # http://localhost:8080

# 3. frontend
cd ../frontend
npm install
npm start                       # http://localhost:4200
```

### Health checks

```bash
curl http://localhost:8080/actuator/health           # {"status":"UP","groups":["liveness","readiness"]}
curl http://localhost:8080/actuator/health/readiness # {"status":"UP"}
curl http://localhost:11434/api/version              # Ollama is up
curl http://localhost:4200                           # frontend serves the app
```

Actuator exposure is restricted to `health,info`, and the health payload reports only the aggregate
status — the per-component `db` and `ollama` detail is **not** exposed, not even with
`?show-details=always` or an admin token. To see why a provider is unhealthy, read the backend
startup log: `OllamaReadinessCheck` logs the real failure reason.

If `KBMS_ADMIN_EMAIL` / `KBMS_ADMIN_PASSWORD` are unset, no bootstrap admin is created and
`AuthConfig` logs a warning.

### Running the tests

```bash
cd backend  && mvn test          # 27 tests; Ollama, database and network are all mocked
cd frontend && npm test          # 12 tests in 3 files
cd frontend && npm run build     # production bundle
```

## 10. What "verified" means in this document

| Label | Meaning |
| --- | --- |
| **Pass** (Playwright) | Observed in a real browser against the running backend, with the recorded HTTP status as evidence |
| **Pass** (API) | Observed status code from the live stack |
| **Pass** (automated) | Part of the 27 backend or 12 frontend tests, which ran in full and reported zero failures |
| **Code read** | Behaviour confirmed by reading the current source, but not exercised at runtime |
| **Not verified** | Could not be checked without creating, editing or deleting data, or because no suitable record exists |

**No claim of "all tests passed" is made beyond the two automated suites**, which were both run in
full on the date of this report (27 backend, 12 frontend, zero failures). Browser checks are listed
individually in §7 with their role and whether they were read-only, and the four items that could
not be checked are named in §7 rather than being implied to have passed.

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------- users
CREATE TABLE app_users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT app_users_role_check CHECK (role IN ('ADMIN', 'EDITOR', 'VIEWER'))
);
CREATE UNIQUE INDEX app_users_email_lower_key ON app_users (lower(email));
CREATE INDEX app_users_role_idx ON app_users (role);

-- ------------------------------------------------------------ categories
-- Name uniqueness is case-insensitive (see the lower(name) indexes below).
CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX categories_name_lower_key ON categories (lower(name));

-- ------------------------------------------------------------------ tags
CREATE TABLE tags (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX tags_name_lower_key ON tags (lower(name));

-- -------------------------------------------------------------- articles
CREATE TABLE knowledge_articles (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(300) NOT NULL,
    summary     VARCHAR(1000),
    content     TEXT        NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    category_id BIGINT REFERENCES categories (id) ON DELETE SET NULL,
    author_id   BIGINT NOT NULL REFERENCES app_users (id),
    updated_by  BIGINT REFERENCES app_users (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    CONSTRAINT knowledge_articles_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);
CREATE INDEX knowledge_articles_status_idx ON knowledge_articles (status);
CREATE INDEX knowledge_articles_category_idx ON knowledge_articles (category_id);
CREATE INDEX knowledge_articles_updated_idx ON knowledge_articles (updated_at DESC);
CREATE INDEX knowledge_articles_title_trgm_idx ON knowledge_articles USING gin (title gin_trgm_ops);

-- Maintained by Postgres, so keyword search never loads the table into app memory.
ALTER TABLE knowledge_articles ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(summary, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(content, '')), 'C')
    ) STORED;
CREATE INDEX knowledge_articles_search_idx ON knowledge_articles USING gin (search_vector);

CREATE TABLE article_tags (
    article_id BIGINT NOT NULL REFERENCES knowledge_articles (id) ON DELETE CASCADE,
    tag_id     BIGINT NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    PRIMARY KEY (article_id, tag_id)
);
CREATE INDEX article_tags_tag_idx ON article_tags (tag_id);

-- -------------------------------------------------------------- documents
CREATE TABLE documents (
    id              BIGSERIAL PRIMARY KEY,
    original_name   VARCHAR(400) NOT NULL,
    storage_name    VARCHAR(200) NOT NULL,
    content_type    VARCHAR(150),
    file_type       VARCHAR(20)  NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED',
    failure_reason  VARCHAR(1000),
    chunk_count     INT          NOT NULL DEFAULT 0,
    uploaded_by     BIGINT       NOT NULL REFERENCES app_users (id),
    uploaded_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at    TIMESTAMPTZ,
    CONSTRAINT documents_status_check CHECK (status IN ('UPLOADED', 'PROCESSING', 'PROCESSED', 'FAILED')),
    CONSTRAINT documents_file_type_check CHECK (file_type IN ('PDF', 'DOCX', 'TXT', 'MARKDOWN'))
);
CREATE UNIQUE INDEX documents_storage_name_key ON documents (storage_name);
CREATE INDEX documents_status_idx ON documents (status);
CREATE INDEX documents_uploaded_at_idx ON documents (uploaded_at DESC);
CREATE INDEX documents_name_trgm_idx ON documents USING gin (original_name gin_trgm_ops);

-- Dimension is a Flyway placeholder (KBMS_VECTOR_DIMENSION). Changing it on an
-- already-migrated database requires a new migration that alters the column.
CREATE TABLE document_chunks (
    id              BIGSERIAL PRIMARY KEY,
    source_type     VARCHAR(20) NOT NULL,
    document_id     BIGINT REFERENCES documents (id) ON DELETE CASCADE,
    article_id      BIGINT REFERENCES knowledge_articles (id) ON DELETE CASCADE,
    source_title    VARCHAR(400) NOT NULL,
    source_status   VARCHAR(20) NOT NULL,
    chunk_index     INT    NOT NULL,
    page_number     INT,
    section         VARCHAR(300),
    content         TEXT   NOT NULL,
    char_start      INT,
    char_end        INT,
    content_length  INT    NOT NULL,
    embedding_model VARCHAR(120),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    embedding       vector(${vector_dimension}),
    CONSTRAINT document_chunks_source_check CHECK (
        (source_type = 'DOCUMENT' AND document_id IS NOT NULL AND article_id IS NULL) OR
        (source_type = 'ARTICLE'  AND article_id  IS NOT NULL AND document_id IS NULL)
    )
);
CREATE INDEX document_chunks_document_idx ON document_chunks (document_id);
CREATE INDEX document_chunks_article_idx  ON document_chunks (article_id);
CREATE INDEX document_chunks_status_idx   ON document_chunks (source_status);
-- Not an IVFFlat/HNSW index: expected MVP volume does not justify one yet.
-- Add one with pgvector >= 0.5 once the corpus justifies the build cost.

-- --------------------------------------------------------------- chat
CREATE TABLE chat_sessions (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    title      VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX chat_sessions_user_idx ON chat_sessions (user_id, updated_at DESC);

CREATE TABLE chat_messages (
    id             BIGSERIAL PRIMARY KEY,
    session_id     BIGINT NOT NULL REFERENCES chat_sessions (id) ON DELETE CASCADE,
    role           VARCHAR(20) NOT NULL,
    content        TEXT    NOT NULL,
    citations      TEXT,
    grounding      VARCHAR(20),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chat_messages_role_check CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT chat_messages_grounding_check CHECK (grounding IS NULL OR grounding IN ('ANSWERED', 'NO_CONTEXT'))
);
CREATE INDEX chat_messages_session_idx ON chat_messages (session_id, created_at);

-- -------------------------------------------------------------- audit
CREATE TABLE audit_events (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT REFERENCES app_users (id) ON DELETE SET NULL,
    actor_email VARCHAR(320),
    action      VARCHAR(60) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id   VARCHAR(64),
    detail      VARCHAR(1000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_events_created_idx ON audit_events (created_at DESC);
CREATE INDEX audit_events_action_idx ON audit_events (action);
CREATE INDEX audit_events_actor_idx ON audit_events (actor_id);

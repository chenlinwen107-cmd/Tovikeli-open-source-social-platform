-- V1: Initial schema.
--
-- Conventions (locked decisions):
--  * PublicId stored as TEXT PRIMARY KEY (domain identity, no DB surrogate id).
--  * Domain Long timestamps (unix ms) stored as BIGINT.
--  * Domain enums stored as TEXT. Only tag.origin gets a CHECK constraint in V1.
--  * metadata stored as JSONB, default '{}'.
--  * Source.name has NO unique constraint (no confirmed domain rule).
--  * sha256 has NO unique constraint; normal index only (V1 dedup lookup key).
--  * All FKs use ON DELETE RESTRICT / NO ACTION. Logical delete via domain status.

CREATE TABLE source (
    id      TEXT PRIMARY KEY,
    name    TEXT NOT NULL,
    type    TEXT NOT NULL,
    enabled BOOLEAN NOT NULL
);

CREATE TABLE instance (
    id                    TEXT PRIMARY KEY,
    source_id             TEXT NOT NULL REFERENCES source (id) ON DELETE RESTRICT,
    domain                TEXT NOT NULL UNIQUE,
    display_name          TEXT,
    enabled               BOOLEAN NOT NULL,
    allow_index           BOOLEAN NOT NULL,
    allow_media_preview   BOOLEAN NOT NULL,
    allow_media_cache     BOOLEAN NOT NULL,
    allow_original_access BOOLEAN NOT NULL,
    nsfw_policy           TEXT,
    health_status         TEXT NOT NULL
);

CREATE TABLE author (
    id                TEXT PRIMARY KEY,
    instance_id       TEXT NOT NULL REFERENCES instance (id) ON DELETE RESTRICT,
    source_author_id  TEXT NOT NULL,
    name              TEXT NOT NULL,
    username          TEXT NOT NULL,
    display_name      TEXT,
    url               TEXT,
    avatar            TEXT,
    bio               TEXT,
    status            TEXT,
    CONSTRAINT uq_author_natural_key UNIQUE (instance_id, source_author_id)
);

CREATE TABLE content (
    id                  TEXT PRIMARY KEY,
    instance_id         TEXT NOT NULL REFERENCES instance (id) ON DELETE RESTRICT,
    source_content_id   TEXT NOT NULL,
    author_id           TEXT NOT NULL REFERENCES author (id) ON DELETE RESTRICT,
    source_id           TEXT NOT NULL REFERENCES source (id) ON DELETE RESTRICT,
    text                TEXT,
    language            TEXT,
    visibility          TEXT NOT NULL,
    status              TEXT NOT NULL,
    parent_id           TEXT REFERENCES content (id) ON DELETE RESTRICT,
    repost_of_id        TEXT REFERENCES content (id) ON DELETE RESTRICT,
    quote_of_id         TEXT REFERENCES content (id) ON DELETE RESTRICT,
    external_created_at BIGINT,
    created_at          BIGINT NOT NULL,
    updated_at          BIGINT NOT NULL,
    metadata            JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uq_content_natural_key UNIQUE (instance_id, source_content_id)
);

CREATE TABLE media (
    id            TEXT PRIMARY KEY,
    sha256        TEXT,
    source_id     TEXT NOT NULL REFERENCES source (id) ON DELETE RESTRICT,
    instance_id   TEXT NOT NULL REFERENCES instance (id) ON DELETE RESTRICT,
    thumbnail_url TEXT,
    preview_url   TEXT,
    original_url  TEXT,
    alt_text      TEXT,
    license       TEXT,
    metadata      JSONB NOT NULL DEFAULT '{}'::jsonb,
    status        TEXT NOT NULL,
    created_at    BIGINT NOT NULL,
    updated_at    BIGINT NOT NULL
);

CREATE INDEX idx_media_sha256 ON media (sha256);

-- Content <-> Media ordered association table.
CREATE TABLE content_media (
    content_id TEXT NOT NULL REFERENCES content (id) ON DELETE RESTRICT,
    media_id   TEXT NOT NULL REFERENCES media (id) ON DELETE RESTRICT,
    "position" INT  NOT NULL,
    PRIMARY KEY (content_id, media_id)
);

CREATE INDEX idx_content_media_media_id ON content_media (media_id);

CREATE TABLE tag (
    id              TEXT PRIMARY KEY,
    name            TEXT NOT NULL,
    normalized_name TEXT,
    origin          TEXT NOT NULL,
    source_tag_id   TEXT,
    instance_id     TEXT,
    source_id       TEXT,
    created_at      BIGINT NOT NULL,
    updated_at      BIGINT NOT NULL,
    CONSTRAINT fk_tag_instance FOREIGN KEY (instance_id) REFERENCES instance (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tag_source   FOREIGN KEY (source_id)   REFERENCES source (id)   ON DELETE RESTRICT,
    CONSTRAINT ck_tag_origin_shape CHECK (
        (origin = 'PLATFORM' AND source_tag_id IS NULL AND instance_id IS NULL AND source_id IS NULL)
        OR
        (origin = 'EXTERNAL' AND source_tag_id IS NOT NULL AND instance_id IS NOT NULL AND source_id IS NOT NULL)
    )
);

-- Partial unique indexes keep PLATFORM and EXTERNAL identity spaces separate.
-- PLATFORM identity = verbatim name.
-- EXTERNAL identity = (instance_id, source_tag_id).
CREATE UNIQUE INDEX uq_tag_platform ON tag (name) WHERE origin = 'PLATFORM';
CREATE UNIQUE INDEX uq_tag_external ON tag (instance_id, source_tag_id) WHERE origin = 'EXTERNAL';

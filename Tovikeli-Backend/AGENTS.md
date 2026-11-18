# ContentPlatformBackend - Engineering Rules

## Project

ContentPlatformBackend is the backend of a content discovery and creator ecosystem platform.

Architecture concept:

Social dynamic content system
+
Artwork / creator ecosystem

The platform will eventually have its own:
- users
- creators
- content
- tags
- interactions
- search
- feed
- timeline
- recommendation systems

External platforms are content sources, not the platform itself.

---

## Current Technology Stack

- Kotlin
- Spring Boot
- Gradle
- Java 21
- PostgreSQL
- Flyway

Current project versions are already established.

Do NOT change existing Kotlin, Spring Boot, Gradle, or Java versions unless explicitly requested.

---

## Core Architecture

The core data flow is:

Third-party API
    ↓
Source Adapter
    ↓
Adapter DTO
    ↓
Normalizer
    ↓
Domain Object
    ↓
Repository
    ↓
Database

API flow:

Domain Object
    ↓
API DTO
    ↓
Frontend

---

## Layer Responsibilities

api/
    HTTP controllers and API DTOs.

domain/
    Core business/domain models and value objects.
    Must not depend on Spring, JPA, HTTP clients, or third-party APIs.

application/
    Application use cases and orchestration.

infrastructure/
    Infrastructure implementations.

adapter/
    External platform integrations.
    Adapters communicate with third-party APIs and convert external data into Adapter DTOs.

repository/
    Repository abstractions and data access contracts.

database/
    Database persistence implementation and database-related code.

cache/
    Cache-related implementations.

config/
    Spring and application configuration.

---

## External Source Rules

Initial external sources:

1. Misskey
2. Mastodon
3. Wikimedia Commons
4. Openverse
5. Pixelfed

X and unofficial Pixiv APIs are excluded.

Flickr is excluded.

Do not implement full federation crawling.

Misskey and Mastodon use an Instance Whitelist model.

Only instances with allowIndex=true may participate in public search/indexing.

---

## SourceAdapter Rules

SourceAdapter responsibilities:

- call third-party APIs
- parse third-party responses
- convert them into Adapter DTOs
- perform source-specific health checks

Adapters MUST NOT:

- write directly to core database
- expose raw third-party JSON to core/domain layers
- implement global ranking
- implement global cache
- implement global circuit breaker
- implement global retry policy
- permanently download original media by default

---

## Domain Rules

External Author is NOT the same as Platform User.

External identity uniqueness:

(instance, sourceAuthorId)

External username must remain unchanged.

Do not modify an external username to create a platform username.

Example:

External username:
alice

Domain value:
alice

UI may display:
Misskey · Alice
Mastodon · Alice

but the stored external username remains:
alice

---

## Public IDs

Database internal IDs must NOT be exposed through public APIs.

Public IDs use typed prefixes, for example:

Content:
ct_xxxxxxxx

Author:
au_xxxxxxxx

Media:
md_xxxxxxxx

Tag:
tg_xxxxxxxx

PublicId is a domain value object.

Do not expose database auto-increment IDs through API responses.

---

## Media Rules

Media represents one logical media resource.

Do NOT create separate Media objects for:

- thumbnail
- preview
- original

Instead:

Media
├── thumbnail
├── preview
└── original

Preferred media flow:

Source thumbnail
    ↓
Source preview

Preview target size is approximately:
1600-2560 px

Original media is NOT permanently stored by default.

Original access must respect:

- instance permissions
- source permissions
- license/rights
- current access validity

---

## Media Deduplication

V1:

SHA-256 for exact file deduplication.

pHash may be used later for approximate visual deduplication.

Do NOT introduce:

- CLIP
- OCR
- embeddings
- vector database
- AI image deduplication

unless explicitly requested.

---

## Content Rules

Content uniqueness:

(instanceId, sourceContentId)

External reposts must reference the original content.

Do not duplicate the original content for a repost.

Relations:

parentId
repostOfId
quoteOfId

Only clearly public content may enter public search.

Deleted external content should normally remain as historical database records rather than immediate physical deletion.

---

## Tags

External Tag and Platform Tag are different concepts.

Tag origins:

PLATFORM
EXTERNAL

normalizedName may be used for search normalization.

Do NOT assume semantic equivalence between different tag names.

Example:

bluearchive
BlueArchive
BLUEARCHIVE

may normalize together.

But:

BA
BlueArchive

must NOT automatically be treated as semantically identical.

---

## Interactions

External interactions and platform interactions are different.

External examples:

- Like
- Favorite
- Reaction
- Repost
- Reply

Platform V1 interaction types:

- LIKE
- FAVORITE
- REPOST
- BOOKMARK
- COMMENT

A user's platform Like must create a platform interaction.

External interactions must not automatically become platform interactions unless an explicit Interaction Mapper exists.

---

## Search V1

Search uses PostgreSQL Full-Text Search.

V1 supports:

- full-text search
- tag search
- author search
- time sorting
- source filtering
- type filtering
- basic ranking

Do NOT introduce:

- Elasticsearch
- OpenSearch
- Solr
- Vector DB
- complex recommendation infrastructure

unless explicitly requested.

---

## API

API prefix:

/api/v1

Initial endpoints:

GET /api/v1/search
GET /api/v1/contents/:id
GET /api/v1/authors/:id
GET /api/v1/tags/:id
GET /api/v1/media/:id

API response envelope:

{
  "data": {},
  "meta": {},
  "error": null
}

Errors use:

{
  "data": null,
  "meta": {},
  "error": {
    "code": "CONTENT_NOT_FOUND",
    "message": "Content not found"
  }
}

---

## Development Rules

Do not make large speculative changes.

Prefer:

1. small change
2. compile
3. test
4. inspect diff
5. continue

Do not introduce dependencies unless explicitly requested.

Do not modify established architecture without discussing the structural reason first.

If an implementation appears to require an architectural change:

STOP.

Explain:
- what conflict was found
- why the current design is insufficient
- what alternatives exist

Wait for approval before changing architecture.

---

## OpenCode Role

OpenCode is an engineering implementation assistant.

OpenCode may:

- inspect the repository
- create files
- modify explicitly requested files
- run Gradle
- run tests
- perform controlled refactoring
- report compiler/test errors

OpenCode must NOT:

- redesign the architecture independently
- introduce unrelated dependencies
- rewrite large parts of the project without approval
- delete established code without approval
- silently change public API contracts
- silently change domain concepts
- introduce complex infrastructure without approval

When uncertain:

STOP AND ASK.

---

## Current Development Stage

Backend Implementation.

Completed:

11.1 Backend Project
11.2 Gradle
11.3 Spring Boot
11.4 Backend Directory Architecture

11.5 Domain Layer — COMPLETED and closed with acceptance.

Completed in 11.5:

- PublicId (generic value object; basic validity only, no type prefix enforcement)
- IdentityHash (SHA-256, lowercase hex, first 32 chars; JDK-only, no dependencies)
- ContentVisibility / ContentStatus
- SourceType / InstanceHealthStatus / Source / Instance
- TagOrigin / Tag
- Author
- PlatformInteractionType
- Media / MediaStatus / MediaUrl
- Media sourceEnabled accessibility fix (canAccessOriginal / canProvidePreview)
- Media dead-code cleanup (isLicenseAllowedForOriginalAccess removed)
- Domain Layer overall consistency review — completed

Current test status:

- 79 tests, 0 failures, 0 errors

Confirmed identity rules:

- PublicId / IdentityHash approach is confirmed.
- Content / Author / External Tag / Platform Tag PublicId derivation is confirmed.
- Media identity (md_) remains explicitly DEFERRED until the Adapter layer is established.
  sha256 remains only the V1 exact-file deduplication key. No sourceMediaId.

Next planned step:

Repository / Persistence Architecture / Repository Contract Design.

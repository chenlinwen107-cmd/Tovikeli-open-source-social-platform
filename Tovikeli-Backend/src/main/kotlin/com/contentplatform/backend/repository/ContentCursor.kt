package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId

/**
 * Stateless keyset cursor for public-content browse pagination (Stage 14).
 *
 * The cursor identifies the position of the LAST DELIVERED content row of a
 * page using the frozen browse ordering key (created_at DESC, id DESC).
 *
 *  - [createdAt] = content.created_at (unix ms, BIGINT in PostgreSQL)
 *  - [id] = content.id (a content [PublicId])
 *
 * The cursor is fully self-contained: no cursor table, session, Redis or
 * server-side token is involved. It is deliberately Content-specific and is
 * NOT a generic pagination framework. Encoding to/from the HTTP Base64URL
 * string form belongs to the API boundary ([ContentCursorCodec] in the api
 * package), so the repository only ever sees the parsed values.
 */
data class ContentCursor(
    val createdAt: Long,
    val id: PublicId
)

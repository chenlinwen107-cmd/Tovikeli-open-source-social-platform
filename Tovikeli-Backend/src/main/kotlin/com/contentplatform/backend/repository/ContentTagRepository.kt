package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId

/**
 * Data access contract for the Content <-> Tag many-to-many association
 * table (`content_tag`, Stage 15-B).
 *
 * This repository ONLY owns `content_tag` persistence. It never creates a
 * Content or a Tag aggregate; the callers (Application use cases) resolve the
 * content id and the tag ids first.
 *
 * Repository contracts are transaction-agnostic. Transaction boundaries belong
 * to Application use cases, not to this interface.
 *
 * Idempotency contract: attachments are keyed by the table's composite primary
 * key (content_id, tag_id). Re-attaching an existing pair is a no-op
 * (ON CONFLICT DO NOTHING at the JDBC implementation); attaching never
 * replaces or removes other associations of the same content.
 */
interface ContentTagRepository {

    /**
     * Attaches one [tagId] to [contentId]. A no-op when the pair already
     * exists.
     */
    fun attach(contentId: PublicId, tagId: PublicId)

    /**
     * Attaches every id in [tagIds] to [contentId] in ONE batched write (no
     * per-tag query/transaction). A no-op when [tagIds] is empty.
     */
    fun attachAll(contentId: PublicId, tagIds: List<PublicId>)
}

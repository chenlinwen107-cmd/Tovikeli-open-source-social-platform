package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.media.Media
import com.contentplatform.backend.domain.media.MediaKind

interface MediaRepository {
    fun findById(id: PublicId): Media?
    fun findBySha256(sha256: String): Media?
    fun save(media: Media): Media
    fun findByKind(kind: MediaKind, limit: Int): List<Media> =
        throw UnsupportedOperationException("findByKind requires persisted media kind")
}

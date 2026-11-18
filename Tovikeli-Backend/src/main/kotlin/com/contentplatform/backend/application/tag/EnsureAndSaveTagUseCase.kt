package com.contentplatform.backend.application.tag

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.tag.Tag
import com.contentplatform.backend.repository.TagRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Ensures that a tag is persisted, keyed by its origin-specific identity:
 *  - PLATFORM tags are keyed by verbatim `name`;
 *  - EXTERNAL tags are keyed by (instanceId, sourceTagId).
 *
 * IMPORTANT: this use case does NOT rely on any upsert guarantee of
 * [TagRepository.save]. The flow is an explicit existence check first:
 *  - when the tag already exists, the existing aggregate is returned and save()
 *    is never invoked;
 *  - only when the tag does NOT exist is a new aggregate created and saved.
 *
 * Transaction notes: at most one repository write, atomic by itself; no
 * transaction boundary is declared here. Outer multi-repository callers own
 * the transaction.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class EnsureAndSaveTagUseCase(
    private val tagRepository: TagRepository
) {

    /**
     * Ensures a PLATFORM tag for the given verbatim [name] exists.
     */
    fun ensurePlatform(
        name: String,
        normalizedName: String? = null
    ): Tag {
        tagRepository.findPlatformByName(name)?.let { existing ->
            return existing
        }

        val tag = Tag.createPlatform(name = name, normalizedName = normalizedName)
        return tagRepository.save(tag)
    }

    /**
     * Ensures an EXTERNAL tag for (instanceId, sourceTagId) exists.
     */
    fun ensureExternal(
        name: String,
        normalizedName: String?,
        sourceTagId: String,
        instanceId: PublicId,
        sourceId: PublicId
    ): Tag {
        tagRepository.findByInstanceAndSourceTagId(instanceId, sourceTagId)?.let { existing ->
            return existing
        }

        val tag = Tag.createFromAdapter(
            name = name,
            normalizedName = normalizedName,
            sourceTagId = sourceTagId,
            instanceId = instanceId,
            sourceId = sourceId
        )
        return tagRepository.save(tag)
    }
}

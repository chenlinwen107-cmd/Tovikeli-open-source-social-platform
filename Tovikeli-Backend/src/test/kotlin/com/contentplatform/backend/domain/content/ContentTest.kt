package com.contentplatform.backend.domain.content

import com.contentplatform.backend.domain.common.IdentityHash
import com.contentplatform.backend.domain.common.PublicId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ContentTest {

    // ------------------------------------------------------------------
    // createFromAdapter
    // ------------------------------------------------------------------

    @Test
    fun createFromAdapter_derivesCtPrefixedSha256Id() {
        val content = sampleContent(instanceIdValue = "inst-1", sourceContentId = "src-1")

        // Known SHA-256 vector: sha256("inst-1:src-1") first 32 hex chars
        assertEquals("9d09cc2b541dcc955cda7c31a8365559", IdentityHash.derive("inst-1:src-1"))
        assertEquals("ct_9d09cc2b541dcc955cda7c31a8365559", content.id.value)
        assertTrue(content.id.value.startsWith("ct_"))
    }

    @Test
    fun createFromAdapter_idFormatIsPrefixPlus32LowercaseHex() {
        val content = sampleContent()

        assertTrue(content.id.value.matches(Regex("^ct_[0-9a-f]{32}$")))
        assertEquals(35, content.id.value.length) // "ct_" + 32 chars
    }

    @Test
    fun createFromAdapter_longNaturalKeyStillProducesFixedLengthId() {
        val longId = "a".repeat(100)
        val content = sampleContent(instanceIdValue = "inst-1", sourceContentId = longId)

        assertTrue(content.id.value.matches(Regex("^ct_[0-9a-f]{32}$")))
        assertEquals(35, content.id.value.length)
    }

    @Test
    fun createFromAdapter_sameNaturalKeyProducesSameId() {
        val a = sampleContent(instanceIdValue = "inst-a", sourceContentId = "src-42")
        val b = sampleContent(instanceIdValue = "inst-a", sourceContentId = "src-42")

        assertEquals(a.id, b.id)
        assertEquals(a.getDeduplicationKey(), b.getDeduplicationKey())
    }

    @Test
    fun createFromAdapter_differentInstanceProducesDifferentId() {
        val a = sampleContent(instanceIdValue = "inst-a", sourceContentId = "src-42")
        val b = sampleContent(instanceIdValue = "inst-b", sourceContentId = "src-42")

        assertNotEquals(a.id, b.id)
    }

    @Test
    fun createFromAdapter_differentSourceContentIdProducesDifferentId() {
        val a = sampleContent(instanceIdValue = "inst-a", sourceContentId = "src-42")
        val b = sampleContent(instanceIdValue = "inst-a", sourceContentId = "src-43")

        assertNotEquals(a.id, b.id)
    }

    @Test
    fun createFromAdapter_setsPublicIdFields() {
        val content = Content.createFromAdapter(
            instanceIdValue = "inst-1",
            sourceContentId = "src-1",
            authorId = PublicId("au_author1"),
            sourceId = PublicId("src_id1"),
            text = "hello",
            language = "en",
            visibility = ContentVisibility.PUBLIC,
            mediaIds = listOf(PublicId("md_media1"), PublicId("md_media2")),
            parentId = null,
            repostOfId = null,
            quoteOfId = null,
            externalCreatedAt = null,
            metadata = emptyMap()
        )

        assertEquals(PublicId("inst-1"), content.instanceId)
        assertEquals(PublicId("src_id1"), content.sourceId)
        assertEquals(PublicId("au_author1"), content.authorId)
    }

    @Test
    fun createFromAdapter_initialStatusIsActive() {
        val content = sampleContent()
        assertEquals(ContentStatus.ACTIVE, content.status)
    }

    @Test
    fun createFromAdapter_passesExternalCreatedAt() {
        val expected = 1_700_000_000_000L
        val content = sampleContent(externalCreatedAt = expected)
        assertEquals(expected, content.externalCreatedAt)
    }

    @Test
    fun createFromAdapter_passesNullableFields() {
        val content = Content.createFromAdapter(
            instanceIdValue = "inst-1",
            sourceContentId = "src-1",
            authorId = PublicId("au_author1"),
            sourceId = PublicId("src_id1"),
            text = "with text",
            language = null,
            visibility = ContentVisibility.UNLISTED,
            mediaIds = listOf(),
            parentId = PublicId("ct_parent"),
            repostOfId = null,
            quoteOfId = PublicId("ct_quote"),
            externalCreatedAt = 1_700_000_000_000L,
            metadata = emptyMap()
        )

        assertEquals("with text", content.text)
        assertEquals(null, content.language)
        assertEquals(ContentVisibility.UNLISTED, content.visibility)
        assertEquals(listOf(), content.mediaIds)
        assertEquals(PublicId("ct_parent"), content.parentId)
        assertEquals(null, content.repostOfId)
        assertEquals(PublicId("ct_quote"), content.quoteOfId)
    }

    @Test
    fun createFromAdapter_setsTimestamps() {
        val before = System.currentTimeMillis()
        val content = Content.createFromAdapter(
            instanceIdValue = "inst-1",
            sourceContentId = "src-1",
            authorId = PublicId("au_author1"),
            sourceId = PublicId("src_id1"),
            text = null,
            language = null,
            visibility = ContentVisibility.PUBLIC,
            mediaIds = listOf(),
            parentId = null,
            repostOfId = null,
            quoteOfId = null,
            externalCreatedAt = null,
            metadata = emptyMap()
        )
        val after = System.currentTimeMillis()

        assertNotNull(content.createdAt)
        assertNotNull(content.updatedAt)
        assertTrue(content.createdAt >= before && content.createdAt <= after)
        assertTrue(content.updatedAt >= before && content.updatedAt <= after)
    }

    // ------------------------------------------------------------------
    // isPubliclyIndexable
    // ------------------------------------------------------------------

    @Test
    fun isPubliclyIndexable_activePublicAndAllowIndex_returnsTrue() {
        val content = sampleContent(
            status = ContentStatus.ACTIVE,
            visibility = ContentVisibility.PUBLIC
        )
        assertTrue(content.isPubliclyIndexable(true))
    }

    @Test
    fun isPubliclyIndexable_nonPublicVisibility_returnsFalse() {
        for (v in listOf(
            ContentVisibility.UNLISTED,
            ContentVisibility.FOLLOWERS,
            ContentVisibility.PRIVATE,
            ContentVisibility.UNKNOWN
        )) {
            val content = sampleContent(visibility = v)
            assertFalse(content.isPubliclyIndexable(true), "visibility=$v should not be indexable")
        }
    }

    @Test
    fun isPubliclyIndexable_nonActiveStatus_returnsFalse() {
        for (s in listOf(
            ContentStatus.DELETED,
            ContentStatus.UNAVAILABLE,
            ContentStatus.HIDDEN,
            ContentStatus.UNKNOWN
        )) {
            val content = sampleContent(status = s)
            assertFalse(content.isPubliclyIndexable(true), "status=$s should not be indexable")
        }
    }

    @Test
    fun isPubliclyIndexable_allowIndexFalse_returnsFalse() {
        val content = sampleContent()
        assertFalse(content.isPubliclyIndexable(false))
    }

    // ------------------------------------------------------------------
    // isExternalRepost
    // ------------------------------------------------------------------

    @Test
    fun isExternalRepost_repostWithoutParent_returnsTrue() {
        val content = sampleContent(
            parentId = null,
            repostOfId = PublicId("ct_original")
        )
        assertTrue(content.isExternalRepost())
    }

    @Test
    fun isExternalRepost_hasParent_returnsFalse() {
        val content = sampleContent(
            parentId = PublicId("ct_parent"),
            repostOfId = PublicId("ct_original")
        )
        assertFalse(content.isExternalRepost())
    }

    @Test
    fun isExternalRepost_noRepostOf_returnsFalse() {
        val content = sampleContent(repostOfId = null)
        assertFalse(content.isExternalRepost())
    }

    // ------------------------------------------------------------------
    // getDeduplicationKey
    // ------------------------------------------------------------------

    @Test
    fun getDeduplicationKey_formatsInstanceAndSourceContentId() {
        val content = sampleContent(
            instanceIdValue = "inst-a",
            sourceContentId = "src-42"
        )
        assertEquals("inst-a:src-42", content.getDeduplicationKey())
    }

    @Test
    fun getDeduplicationKey_sameInstanceAndSourceContentId_sameKey() {
        val a = sampleContent(instanceIdValue = "inst-a", sourceContentId = "src-42")
        val b = sampleContent(instanceIdValue = "inst-a", sourceContentId = "src-42")
        assertEquals(a.getDeduplicationKey(), b.getDeduplicationKey())
    }

    @Test
    fun getDeduplicationKey_differentInstanceOrSourceContentId_differentKey() {
        val base = sampleContent(instanceIdValue = "inst-a", sourceContentId = "src-42")
        val differentInstance = sampleContent(instanceIdValue = "inst-b", sourceContentId = "src-42")
        val differentSource = sampleContent(instanceIdValue = "inst-a", sourceContentId = "src-43")

        assertNotEquals(base.getDeduplicationKey(), differentInstance.getDeduplicationKey())
        assertNotEquals(base.getDeduplicationKey(), differentSource.getDeduplicationKey())
    }

    // ------------------------------------------------------------------
    // updateStatus
    // ------------------------------------------------------------------

    @Test
    fun updateStatus_returnsNewContentWithUpdatedStatusAndTimestamp() {
        val original = sampleContent(status = ContentStatus.ACTIVE)
        val updated = original.updateStatus(ContentStatus.HIDDEN)

        assertNotEquals(original, updated)
        assertEquals(ContentStatus.HIDDEN, updated.status)
        assertTrue(updated.updatedAt >= original.updatedAt)
    }

    @Test
    fun updateStatus_doesNotMutateOriginal() {
        val original = sampleContent(status = ContentStatus.ACTIVE)
        val before = original.updatedAt

        original.updateStatus(ContentStatus.DELETED)

        assertEquals(ContentStatus.ACTIVE, original.status)
        assertEquals(before, original.updatedAt)
    }

    // ------------------------------------------------------------------
    // data class value equality
    // ------------------------------------------------------------------

    @Test
    fun content_valueEquality_withSameFields() {
        val a = sampleContent()
        val b = sampleContent()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // ------------------------------------------------------------------
    // sourceContentId preserved as-is
    // ------------------------------------------------------------------

    @Test
    fun createFromAdapter_preservesSourceContentIdExactly() {
        val rawSourceContentId = "  MiXeD-CaSe_External-ID  "
        val content = Content.createFromAdapter(
            instanceIdValue = "inst-1",
            sourceContentId = rawSourceContentId,
            authorId = PublicId("au_author1"),
            sourceId = PublicId("src_id1"),
            text = null,
            language = null,
            visibility = ContentVisibility.PUBLIC,
            mediaIds = listOf(),
            parentId = null,
            repostOfId = null,
            quoteOfId = null,
            externalCreatedAt = null,
            metadata = emptyMap()
        )

        assertEquals(rawSourceContentId, content.sourceContentId)
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun emptyMap(): Map<String, Any?> = mapOf()

    private fun sampleContent(
        instanceIdValue: String = "inst-1",
        sourceContentId: String = "src-1",
        status: ContentStatus = ContentStatus.ACTIVE,
        visibility: ContentVisibility = ContentVisibility.PUBLIC,
        parentId: PublicId? = null,
        repostOfId: PublicId? = null,
        quoteOfId: PublicId? = null,
        externalCreatedAt: Long? = null
    ): Content {
        return Content(
            id = PublicId("ct_" + IdentityHash.derive("$instanceIdValue:$sourceContentId")),
            instanceId = PublicId(instanceIdValue),
            sourceContentId = sourceContentId,
            authorId = PublicId("au_author1"),
            sourceId = PublicId("src_id1"),
            text = "hello",
            language = "en",
            visibility = visibility,
            status = status,
            mediaIds = listOf(PublicId("md_media1")),
            parentId = parentId,
            repostOfId = repostOfId,
            quoteOfId = quoteOfId,
            topicId = null,
            externalCreatedAt = externalCreatedAt,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            metadata = emptyMap()
        )
    }
}

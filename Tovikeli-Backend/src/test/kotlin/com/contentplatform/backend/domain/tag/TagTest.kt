package com.contentplatform.backend.domain.tag

import com.contentplatform.backend.domain.common.IdentityHash
import com.contentplatform.backend.domain.common.PublicId
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class TagTest {

    // ------------------------------------------------------------------
    // createPlatform
    // ------------------------------------------------------------------

    @Test
    fun createPlatform_derivesTgPrefixedSha256IdFromName() {
        val tag = Tag.createPlatform(
            name = "Kotlin",
            normalizedName = "kotlin"
        )

        // Known SHA-256 vector: sha256("Kotlin") first 32 hex chars
        assertEquals("c78f6c97923e81a2f04f09c5e87b69e0", IdentityHash.derive("Kotlin"))
        assertEquals("tg_c78f6c97923e81a2f04f09c5e87b69e0", tag.id.value)
        assertTrue(tag.id.value.startsWith("tg_"))
    }

    @Test
    fun createPlatform_idFormatIsPrefixPlus32LowercaseHex() {
        val tag = Tag.createPlatform(
            name = "a".repeat(100),
            normalizedName = null
        )

        assertTrue(tag.id.value.matches(Regex("^tg_[0-9a-f]{32}$")))
        assertEquals(35, tag.id.value.length) // "tg_" + 32 chars
    }

    @Test
    fun createPlatform_sameNameProducesSameId() {
        val a = Tag.createPlatform(name = "Kotlin", normalizedName = "kotlin")
        val b = Tag.createPlatform(name = "Kotlin", normalizedName = "kotlin")

        assertEquals(a.id, b.id)
    }

    @Test
    fun createPlatform_differentNameProducesDifferentId() {
        val a = Tag.createPlatform(name = "Kotlin", normalizedName = "kotlin")
        val b = Tag.createPlatform(name = "kotlin", normalizedName = "kotlin")

        assertNotEquals(a.id, b.id)
    }

    @Test
    fun createPlatform_setsNameAndNormalizedName() {
        val tag = Tag.createPlatform(
            name = "  Kotlin JVM  ",
            normalizedName = "kotlin jvm"
        )

        assertEquals("  Kotlin JVM  ", tag.name)
        assertEquals("kotlin jvm", tag.normalizedName)
    }

    @Test
    fun createPlatform_originIsPlatform() {
        val tag = Tag.createPlatform(
            name = "Kotlin",
            normalizedName = "kotlin"
        )

        assertEquals(TagOrigin.PLATFORM, tag.origin)
    }

    @Test
    fun createPlatform_externalFieldsAreNull() {
        val tag = Tag.createPlatform(
            name = "Kotlin",
            normalizedName = "kotlin"
        )

        assertEquals(null, tag.sourceTagId)
        assertEquals(null, tag.instanceId)
        assertEquals(null, tag.sourceId)
    }

    @Test
    fun createPlatform_setsTimestamps() {
        val before = System.currentTimeMillis()

        val tag = Tag.createPlatform(
            name = "Kotlin",
            normalizedName = "kotlin"
        )

        val after = System.currentTimeMillis()

        assertTrue(tag.createdAt >= before && tag.createdAt <= after)
        assertTrue(tag.updatedAt >= before && tag.updatedAt <= after)
    }

    // ------------------------------------------------------------------
    // createFromAdapter
    // ------------------------------------------------------------------

    @Test
    fun createFromAdapter_derivesTgPrefixedSha256IdFromInstanceAndSourceTagId() {
        val instanceId = PublicId("inst-1")
        val tag = Tag.createFromAdapter(
            name = "Kotlin",
            normalizedName = "kotlin",
            sourceTagId = "src-tag-1",
            instanceId = instanceId,
            sourceId = PublicId("src-1")
        )

        // Known SHA-256 vector: sha256("inst-1:src-tag-1") first 32 hex chars
        assertEquals("1a3ece0e95036f34e391614fbf1911b4", IdentityHash.derive("inst-1:src-tag-1"))
        assertEquals("tg_1a3ece0e95036f34e391614fbf1911b4", tag.id.value)
        assertTrue(tag.id.value.startsWith("tg_"))
    }

    @Test
    fun createFromAdapter_idFormatIsPrefixPlus32LowercaseHex() {
        val tag = Tag.createFromAdapter(
            name = "Kotlin",
            normalizedName = "kotlin",
            sourceTagId = "a".repeat(100),
            instanceId = PublicId("inst-1"),
            sourceId = PublicId("src-1")
        )

        assertTrue(tag.id.value.matches(Regex("^tg_[0-9a-f]{32}$")))
        assertEquals(35, tag.id.value.length)
    }

    @Test
    fun createFromAdapter_sameNaturalKeyProducesSameId() {
        val a = Tag.createFromAdapter(
            name = "Kotlin",
            normalizedName = "kotlin",
            sourceTagId = "src-tag-42",
            instanceId = PublicId("inst-a"),
            sourceId = PublicId("src-a")
        )
        val b = Tag.createFromAdapter(
            name = "Kotlin",
            normalizedName = "kotlin",
            sourceTagId = "src-tag-42",
            instanceId = PublicId("inst-a"),
            sourceId = PublicId("src-a")
        )

        assertEquals(a.id, b.id)
    }

    @Test
    fun createFromAdapter_differentInstanceProducesDifferentId() {
        val a = Tag.createFromAdapter(
            name = "Kotlin",
            normalizedName = "kotlin",
            sourceTagId = "src-tag-42",
            instanceId = PublicId("inst-a"),
            sourceId = PublicId("src-a")
        )
        val b = Tag.createFromAdapter(
            name = "Kotlin",
            normalizedName = "kotlin",
            sourceTagId = "src-tag-42",
            instanceId = PublicId("inst-b"),
            sourceId = PublicId("src-a")
        )

        assertNotEquals(a.id, b.id)
    }

    @Test
    fun createFromAdapter_differentSourceTagIdProducesDifferentId() {
        val a = Tag.createFromAdapter(
            name = "Kotlin",
            normalizedName = "kotlin",
            sourceTagId = "src-tag-42",
            instanceId = PublicId("inst-a"),
            sourceId = PublicId("src-a")
        )
        val b = Tag.createFromAdapter(
            name = "Kotlin",
            normalizedName = "kotlin",
            sourceTagId = "src-tag-43",
            instanceId = PublicId("inst-a"),
            sourceId = PublicId("src-a")
        )

        assertNotEquals(a.id, b.id)
    }

    @Test
    fun createFromAdapter_setsNameAndNormalizedName() {
        val tag = Tag.createFromAdapter(
            name = "  Kotlin JVM  ",
            normalizedName = "kotlin jvm",
            sourceTagId = "src-tag-1",
            instanceId = PublicId("inst-1"),
            sourceId = PublicId("src-1")
        )

        assertEquals("  Kotlin JVM  ", tag.name)
        assertEquals("kotlin jvm", tag.normalizedName)
    }

    @Test
    fun createFromAdapter_originIsExternal() {
        val tag = Tag.createFromAdapter(
            name = "Kotlin",
            normalizedName = "kotlin",
            sourceTagId = "src-tag-1",
            instanceId = PublicId("inst-1"),
            sourceId = PublicId("src-1")
        )

        assertEquals(TagOrigin.EXTERNAL, tag.origin)
    }

    @Test
    fun createFromAdapter_savesExternalReferences() {
        val instanceId = PublicId("inst-1")
        val sourceId = PublicId("src-1")

        val tag = Tag.createFromAdapter(
            name = "Kotlin",
            normalizedName = "kotlin",
            sourceTagId = "src-tag-1",
            instanceId = instanceId,
            sourceId = sourceId
        )

        assertEquals("src-tag-1", tag.sourceTagId)
        assertEquals(instanceId, tag.instanceId)
        assertEquals(sourceId, tag.sourceId)
    }

    @Test
    fun createFromAdapter_setsTimestamps() {
        val before = System.currentTimeMillis()

        val tag = Tag.createFromAdapter(
            name = "Kotlin",
            normalizedName = "kotlin",
            sourceTagId = "src-tag-1",
            instanceId = PublicId("inst-1"),
            sourceId = PublicId("src-1")
        )

        val after = System.currentTimeMillis()

        assertTrue(tag.createdAt >= before && tag.createdAt <= after)
        assertTrue(tag.updatedAt >= before && tag.updatedAt <= after)
    }

    // ------------------------------------------------------------------
    // getDeduplicationKey
    // ------------------------------------------------------------------

    @Test
    fun getDeduplicationKey_platformUsesName() {
        val tag = Tag(
            id = PublicId("tg_platform-1"),
            name = "Kotlin",
            normalizedName = "kotlin",
            origin = TagOrigin.PLATFORM,
            sourceTagId = null,
            instanceId = null,
            sourceId = null,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )

        assertEquals("PLATFORM:Kotlin", tag.getDeduplicationKey())
    }

    @Test
    fun getDeduplicationKey_externalUsesInstanceAndSourceTagId() {
        val tag = Tag(
            id = PublicId("tg_external-1"),
            name = "Kotlin",
            normalizedName = "kotlin",
            origin = TagOrigin.EXTERNAL,
            sourceTagId = "src-tag-42",
            instanceId = PublicId("inst-a"),
            sourceId = PublicId("src-a"),
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )

        assertEquals("EXTERNAL:inst-a:src-tag-42", tag.getDeduplicationKey())
    }

    @Test
    fun getDeduplicationKey_externalWithoutInstanceIdThrows() {
        val tag = Tag(
            id = PublicId("tg_external-1"),
            name = "Kotlin",
            normalizedName = "kotlin",
            origin = TagOrigin.EXTERNAL,
            sourceTagId = "src-tag-42",
            instanceId = null,
            sourceId = PublicId("src-a"),
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )

        var thrown = false

        try {
            tag.getDeduplicationKey()
        } catch (e: IllegalStateException) {
            thrown = true
        }

        assertTrue(thrown)
    }

    @Test
    fun getDeduplicationKey_externalWithoutSourceTagIdThrows() {
        val tag = Tag(
            id = PublicId("tg_external-1"),
            name = "Kotlin",
            normalizedName = "kotlin",
            origin = TagOrigin.EXTERNAL,
            sourceTagId = null,
            instanceId = PublicId("inst-a"),
            sourceId = PublicId("src-a"),
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )

        var thrown = false

        try {
            tag.getDeduplicationKey()
        } catch (e: IllegalStateException) {
            thrown = true
        }

        assertTrue(thrown)
    }

    @Test
    fun getDeduplicationKey_platformAndExternalKeysDoNotCollide() {
        val platformTag = Tag(
            id = PublicId("tg_platform-1"),
            name = "Kotlin",
            normalizedName = "kotlin",
            origin = TagOrigin.PLATFORM,
            sourceTagId = null,
            instanceId = null,
            sourceId = null,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )

        val externalTag = Tag(
            id = PublicId("tg_external-1"),
            name = "Kotlin",
            normalizedName = "kotlin",
            origin = TagOrigin.EXTERNAL,
            sourceTagId = "Kotlin",
            instanceId = PublicId("inst-1"),
            sourceId = PublicId("src-1"),
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )

        assertNotEquals(
            platformTag.getDeduplicationKey(),
            externalTag.getDeduplicationKey()
        )
    }

    // ------------------------------------------------------------------
    // tryNormalize
    // ------------------------------------------------------------------

    @Test
    fun tryNormalize_returnsNormalizedName() {
        val tag = Tag(
            id = PublicId("tg_platform-1"),
            name = "Kotlin JVM",
            normalizedName = "kotlin jvm",
            origin = TagOrigin.PLATFORM,
            sourceTagId = null,
            instanceId = null,
            sourceId = null,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )

        assertEquals("kotlin jvm", tag.tryNormalize())
    }

    @Test
    fun tryNormalize_returnsNullWhenNormalizedNameIsNull() {
        val tag = Tag(
            id = PublicId("tg_platform-1"),
            name = "Kotlin JVM",
            normalizedName = null,
            origin = TagOrigin.PLATFORM,
            sourceTagId = null,
            instanceId = null,
            sourceId = null,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )

        assertEquals(null, tag.tryNormalize())
    }
}

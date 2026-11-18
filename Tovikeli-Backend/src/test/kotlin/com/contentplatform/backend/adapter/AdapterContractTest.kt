package com.contentplatform.backend.adapter

import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.domain.source.SourceType
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AdapterContractTest {

    private val inst = ExternalInstanceRef(instanceDomain = "misskey.io")
    private val source = ExternalSourceRef(sourceType = SourceType.MISSKEY)

    // ------------------------------------------------------------------
    // ExternalAuthorInput
    // ------------------------------------------------------------------

    @Test
    fun authorInput_expressMinimalAuthorData() {
        val input = ExternalAuthorInput(
            instanceRef = inst,
            sourceAuthorId = "9u3h",
            name = "Alice",
            username = "alice"
        )
        assertEquals("misskey.io", input.instanceRef.instanceDomain)
        assertEquals("9u3h", input.sourceAuthorId)
        assertEquals("Alice", input.name)
        assertEquals("alice", input.username)
        assertNull(input.avatarUrl)
        assertNull(input.profileUrl)
        assertTrue(input.metadata.isEmpty())
    }

    @Test
    fun authorInput_nullableAndMetadataFields() {
        val input = ExternalAuthorInput(
            instanceRef = inst,
            sourceAuthorId = "1",
            name = "Bob",
            username = "bob",
            displayName = "Bobby",
            avatarUrl = "https://example.com/av.png",
            profileUrl = "https://misskey.io/@bob",
            bio = "hello",
            metadata = mapOf("verified" to true)
        )
        assertEquals("Bobby", input.displayName)
        assertEquals("https://example.com/av.png", input.avatarUrl)
        assertEquals("https://misskey.io/@bob", input.profileUrl)
        assertEquals("hello", input.bio)
        assertEquals(true, input.metadata["verified"])
    }

    // ------------------------------------------------------------------
    // ExternalMediaInput
    // ------------------------------------------------------------------

    @Test
    fun mediaInput_expressUrlAndSha256() {
        val input = ExternalMediaInput(
            instanceRef = inst,
            sourceUrl = "https://example.com/full.png",
            previewUrl = "https://example.com/preview.png",
            sha256 = "abc123",
            mimeType = "image/png",
            width = 1920,
            height = 1080
        )
        assertEquals("https://example.com/full.png", input.sourceUrl)
        assertEquals("https://example.com/preview.png", input.previewUrl)
        assertEquals("abc123", input.sha256)
        assertEquals("image/png", input.mimeType)
        assertEquals(1920, input.width)
        assertEquals(1080, input.height)
        assertNull(input.originalUrl)
    }

    @Test
    fun mediaInput_nullableFields() {
        val input = ExternalMediaInput(instanceRef = inst)
        assertNull(input.sourceUrl)
        assertNull(input.previewUrl)
        assertNull(input.originalUrl)
        assertNull(input.sha256)
        assertNull(input.mimeType)
        assertNull(input.width)
        assertNull(input.height)
        assertNull(input.altText)
        assertNull(input.license)
        assertTrue(input.metadata.isEmpty())
    }

    // ------------------------------------------------------------------
    // ExternalContentInput
    // ------------------------------------------------------------------

    @Test
    fun contentInput_expressMinimalContentAndReferences() {
        val input = ExternalContentInput(
            instanceRef = inst,
            sourceContentId = "note-42",
            authorRef = "9u3h",
            text = "hello world",
            language = "en",
            externalCreatedAt = 1_700_000_000_000L,
            visibility = ContentVisibility.PUBLIC,
            mediaRefs = listOf("media-1", "media-2"),
            parentRef = "note-41",
            repostOfRef = null,
            quoteOfRef = "note-40"
        )
        assertEquals("note-42", input.sourceContentId)
        assertEquals("9u3h", input.authorRef)
        assertEquals("hello world", input.text)
        assertEquals(listOf("media-1", "media-2"), input.mediaRefs)
        assertEquals("note-41", input.parentRef)
        assertNull(input.repostOfRef)
        assertEquals("note-40", input.quoteOfRef)
    }

    @Test
    fun contentInput_defaults() {
        val input = ExternalContentInput(
            instanceRef = inst,
            sourceContentId = "note-1",
            authorRef = "a1"
        )
        assertNull(input.text)
        assertNull(input.language)
        assertNull(input.externalCreatedAt)
        assertEquals(ContentVisibility.UNKNOWN, input.visibility)
        assertTrue(input.mediaRefs.isEmpty())
        assertNull(input.parentRef)
        assertTrue(input.metadata.isEmpty())
    }

    // ------------------------------------------------------------------
    // ExternalTagInput
    // ------------------------------------------------------------------

    @Test
    fun tagInput_externalKind() {
        val input = ExternalTagInput(
            instanceRef = inst,
            sourceIdRef = source,
            name = "art",
            sourceTagId = "tag-1",
            normalizedName = "art"
        )
        assertEquals("tag-1", input.sourceTagId)
        assertEquals(SourceType.MISSKEY, input.sourceIdRef?.sourceType)
        assertEquals("art", input.name)
        assertEquals("art", input.normalizedName)
    }

    @Test
    fun tagInput_platformKindKeepsVerbatimNameAndNullExternal() {
        // A platform tag shape: only verbatim name is identity-relevant.
        val input = ExternalTagInput(
            instanceRef = inst,
            name = "  Kotlin  ",
            sourceTagId = null,
            normalizedName = null
        )
        assertEquals("  Kotlin  ", input.name, "name must remain verbatim")
        assertNull(input.sourceIdRef)
        assertNull(input.sourceTagId)
        assertNull(input.normalizedName)
    }

    // ------------------------------------------------------------------
    // FetchCommand
    // ------------------------------------------------------------------

    @Test
    fun fetchCommand_expressesSourceInstanceAndCursor() {
        val cmd = FetchCommand(
            sourceType = SourceType.MASTODON,
            instanceDomain = "mastodon.social",
            cursor = "abc",
            since = 1000L,
            until = 2000L
        )
        assertEquals(SourceType.MASTODON, cmd.sourceType)
        assertEquals("mastodon.social", cmd.instanceDomain)
        assertEquals("abc", cmd.cursor)
        assertEquals(1000L, cmd.since)
        assertEquals(2000L, cmd.until)
    }

    @Test
    fun fetchCommand_defaults() {
        val cmd = FetchCommand(sourceType = SourceType.MISSKEY)
        assertNull(cmd.instanceDomain)
        assertNull(cmd.cursor)
        assertNull(cmd.since)
        assertNull(cmd.until)
    }
}

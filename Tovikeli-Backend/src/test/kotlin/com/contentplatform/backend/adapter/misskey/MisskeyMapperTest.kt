package com.contentplatform.backend.adapter.misskey

import com.contentplatform.backend.domain.content.ContentVisibility
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Mapping + sensitive/CW filtering tests for [MisskeyMapper] (P9-F).
 *
 * All fixtures are constructed in-memory (no network, no database).
 */
class MisskeyMapperTest {

    private fun mapper() = MisskeyMapper(instanceDomain = "misskey.art")

    private fun user(id: String = "u1", username: String = "alice", name: String? = "Alice") =
        MisskeyUser(
            id = id,
            username = username,
            name = name,
            avatarUrl = "https://misskey.art/av.png",
            description = "artist"
        )

    private fun file(
        id: String = "f1",
        type: String = "image/png",
        url: String = "https://misskey.art/f1.png",
        thumb: String = "https://misskey.art/f1.thumb.png",
        sensitive: Boolean = false,
        comment: String? = "alt"
    ) = MisskeyFile(
        id = id,
        type = type,
        url = url,
        thumbnailUrl = thumb,
        isSensitive = sensitive,
        comment = comment,
        properties = MisskeyFileProperties(width = 1600, height = 900)
    )

    private fun note(
        id: String = "n1",
        text: String? = "hello",
        cw: String? = null,
        createdAt: String? = "2026-09-08T10:00:00.000Z",
        user: MisskeyUser? = user(),
        replyId: String? = null,
        renoteId: String? = null,
        renote: MisskeyNote? = null,
        visibility: String? = "public",
        lang: String? = "en",
        tags: List<String>? = null,
        files: List<MisskeyFile>? = null,
        sensitive: Boolean = false
    ) = MisskeyNote(
        id = id,
        text = text,
        cw = cw,
        createdAt = createdAt,
        user = user,
        userId = user?.id,
        replyId = replyId,
        renoteId = renoteId,
        renote = renote,
        visibility = visibility,
        lang = lang,
        tags = tags,
        files = files,
        sensitive = sensitive
    )

    // ------------------------------------------------------------------
    // Normal mapping
    // ------------------------------------------------------------------

    @Test
    fun mapPage_mapsAuthorAndContent() {
        val result = mapper().mapPage(listOf(note()))

        assertEquals(1, result.authors.size)
        assertEquals("u1", result.authors[0].sourceAuthorId)
        assertEquals("alice", result.authors[0].username)
        assertEquals("Alice", result.authors[0].displayName)
        assertEquals("https://misskey.art/av.png", result.authors[0].avatarUrl)
        assertEquals("artist", result.authors[0].bio)

        assertEquals(1, result.contents.size)
        val content = result.contents[0]
        assertEquals("n1", content.sourceContentId)
        assertEquals("u1", content.authorRef)
        assertEquals("hello", content.text)
        assertEquals("en", content.language)
        assertEquals(ContentVisibility.PUBLIC, content.visibility)
        assertEquals(1_788_861_600_000L, content.externalCreatedAt)
        assertNull(content.parentRef)
        assertNull(result.nextCursor)
    }

    @Test
    fun mapVisibility_matchesMisskeyVocabulary() {
        val m = mapper()
        assertEquals(ContentVisibility.PUBLIC, m.mapVisibility("public"))
        assertEquals(ContentVisibility.UNLISTED, m.mapVisibility("home"))
        assertEquals(ContentVisibility.FOLLOWERS, m.mapVisibility("followers"))
        assertEquals(ContentVisibility.PRIVATE, m.mapVisibility("specified"))
        assertEquals(ContentVisibility.UNKNOWN, m.mapVisibility(null))
        assertEquals(ContentVisibility.UNKNOWN, m.mapVisibility("something-else"))
    }

    @Test
    fun mapContent_parsesCreatedAtInMillis() {
        val content = mapper().mapContent(note(createdAt = "2026-09-08T10:00:00.000Z"))
        assertEquals(1_788_861_600_000L, content.externalCreatedAt)
    }

    // ------------------------------------------------------------------
    // sensitive / CW filtering (approved decision A)
    // ------------------------------------------------------------------

    @Test
    fun mapPage_filtersSensitiveNote() {
        val result = mapper().mapPage(listOf(note(sensitive = true)))
        assertTrue(result.contents.isEmpty(), "sensitive note must not produce content")
        assertTrue(result.media.isEmpty(), "sensitive note must not produce media")
        assertTrue(result.tags.isEmpty(), "sensitive note must not produce tags")
        assertTrue(result.authors.isEmpty(), "a filtered note contributes no author")
    }

    @Test
    fun mapPage_filtersContentWarningNote() {
        val result = mapper().mapPage(listOf(note(cw = "spoiler")))
        assertTrue(result.contents.isEmpty(), "CW note must not produce content")
        assertTrue(result.media.isEmpty())
        assertTrue(result.tags.isEmpty())
    }

    @Test
    fun mapPage_filtersNoteWithSensitiveFile() {
        val result = mapper().mapPage(
            listOf(note(files = listOf(file(id = "f1", sensitive = true))))
        )
        assertTrue(result.contents.isEmpty(), "note with sensitive file must not produce content")
        assertTrue(result.media.isEmpty(), "sensitive file must not produce media")
    }

    @Test
    fun mapPage_filtersSensitiveOriginalInsideRenoteWrapper() {
        val original = note(id = "orig", text = "hidden", sensitive = true, user = user(id = "u9"))
        val wrapper = note(id = "wrapper", text = null, renoteId = "orig", renote = original)
        val result = mapper().mapPage(listOf(wrapper))
        assertTrue(result.contents.isEmpty(), "a sensitive original must not leak via a wrapper")
    }

    @Test
    fun mapPage_keepsNonSensitiveNotesOnly() {
        val result = mapper().mapPage(
            listOf(
                note(id = "ok", user = user(id = "u1")),
                note(id = "bad", sensitive = true, user = user(id = "u2"))
            )
        )
        assertEquals(1, result.contents.size)
        assertEquals("ok", result.contents[0].sourceContentId)
        assertEquals(1, result.authors.size)
        assertEquals("u1", result.authors[0].sourceAuthorId)
    }

    // ------------------------------------------------------------------
    // Tags
    // ------------------------------------------------------------------

    @Test
    fun mapPage_mapsTagsAsPlatformTags() {
        val result = mapper().mapPage(listOf(note(tags = listOf("art", "anime"))))
        assertEquals(1, result.contents.size)
        assertEquals(listOf("art", "anime"), result.contents[0].tags.map { it.name })
        assertEquals(2, result.tags.size)
        assertTrue(result.tags.all { it.sourceTagId == null }, "Misskey note tags map as platform tags")
    }

    // ------------------------------------------------------------------
    // Media
    // ------------------------------------------------------------------

    @Test
    fun mapPage_mapsMediaCorrelatedByFileId() {
        val result = mapper().mapPage(listOf(note(files = listOf(file(id = "f1")))))
        assertEquals(1, result.contents.size)
        assertEquals(listOf("f1"), result.contents[0].mediaRefs)
        assertEquals(1, result.media.size)

        val media = result.media.getValue("f1")
        assertEquals("https://misskey.art/f1.png", media.sourceUrl)
        assertEquals("https://misskey.art/f1.thumb.png", media.previewUrl)
        assertEquals("https://misskey.art/f1.png", media.originalUrl)
        assertEquals("image/png", media.mimeType)
        assertEquals(1600, media.width)
        assertEquals(900, media.height)
        assertEquals("alt", media.altText)
        assertNull(media.sha256)
    }

    // ------------------------------------------------------------------
    // renote / reply
    // ------------------------------------------------------------------

    @Test
    fun mapPage_mapsRenoteAsRepostWrapper() {
        val original = note(id = "orig", text = "original", user = user(id = "u9"))
        val wrapper = note(id = "wrapper", text = null, renoteId = "orig", renote = original)

        val result = mapper().mapPage(listOf(wrapper))

        assertEquals(1, result.contents.size)
        val content = result.contents[0]
        assertEquals("wrapper", content.sourceContentId, "the wrapper note keeps its own id")
        assertEquals("orig", content.repostOfRef, "renote maps to repostOfRef")
        assertEquals("original", content.text)
        // The booster is the content author; the encoder of a renote is only
        // the wrapper author (the original author is not separately emitted,
        // mirroring MastodonMapper's boost handling).
        assertEquals("u1", content.authorRef)
        assertEquals(1, result.authors.size)
    }

    @Test
    fun mapPage_mapsReplyIdAsParentRef() {
        val result = mapper().mapPage(listOf(note(replyId = "n0")))
        assertEquals("n0", result.contents[0].parentRef)
    }
}

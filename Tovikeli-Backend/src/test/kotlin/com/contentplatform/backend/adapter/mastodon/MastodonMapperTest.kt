package com.contentplatform.backend.adapter.mastodon

import com.contentplatform.backend.adapter.ExternalFetchResult
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.domain.source.SourceType
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MastodonMapperTest {

    private fun mapper() = MastodonMapper(
        sourceType = SourceType.MASTODON,
        instanceDomain = "mastodon.social"
    )

    private fun status(
        id: String = "1001",
        content: String? = "hello",
        createdAt: String = "2026-09-08T10:00:00.000Z",
        visibility: String = "public",
        language: String? = "en",
        inReplyToId: String? = null,
        accountId: String = "9",
        reblog: MastodonStatus? = null,
        quote: MastodonQuote? = null,
        tags: List<MastodonTag>? = null
    ): MastodonStatus = MastodonStatus(
        id = id,
        account = MastodonAccount(
            id = accountId, username = "alice", acct = "alice@mastodon.social",
            display_name = "Alice", avatar = "https://m.social/av.jpg",
            note = "<p>Bio</p>", url = "https://m.social/@alice"
        ),
        content = content,
        created_at = createdAt,
        visibility = visibility,
        media_attachments = null,
        in_reply_to_id = inReplyToId,
        reblog = reblog,
        quote = quote,
        language = language,
        tags = tags
    )

    // ------------------------------------------------------------------
    // Basic mapping -> authors / contents / media
    // ------------------------------------------------------------------

    @Test
    fun mapPage_mapsAuthorAndContent() {
        val mapper = mapper()
        val result = mapper.mapPage(listOf(status()), linkHeader = null)

        assertEquals(1, result.authors.size)
        val author = result.authors[0]
        assertEquals("9", author.sourceAuthorId)
        assertEquals("alice@mastodon.social", author.username)
        assertEquals("Alice", author.displayName)
        assertEquals("https://m.social/av.jpg", author.avatarUrl)
        assertEquals("https://m.social/@alice", author.profileUrl)

        assertEquals(1, result.contents.size)
        val content = result.contents[0]
        assertEquals("1001", content.sourceContentId)
        assertEquals("9", content.authorRef)
        assertEquals("hello", content.text)
        assertEquals("en", content.language)
        assertNull(content.parentRef)
        assertTrue(content.mediaRefs.isEmpty())
        assertEquals(0, result.media.size)
    }

    // ------------------------------------------------------------------
    // externalCreatedAt conversion
    // ------------------------------------------------------------------

    @Test
    fun mapContent_convertsIsoCreatedAtToMillis() {
        val mapper = mapper()
        val content = mapper.mapContent(
            status(id = "200", createdAt = "2026-09-08T10:00:00.000Z")
        )
        assertEquals(1_788_861_600_000L, content.externalCreatedAt)
    }

    // ------------------------------------------------------------------
    // HTML -> text
    // ------------------------------------------------------------------

    @Test
    fun stripHtml_removesTagsAndKeepsText() {
        val mapper = mapper()
        assertEquals("Hello world", mapper.stripHtml("<p>Hello <strong>world</strong></p>"))
    }

    @Test
    fun stripHtml_decodesCommonEntities() {
        val mapper = mapper()
        assertEquals("a & b < c > d", mapper.stripHtml("<p>a &amp; b &lt; c &gt; d</p>"))
    }

    @Test
    fun stripHtml_returnsPlainTextUnchanged() {
        val mapper = mapper()
        assertEquals("no html here", mapper.stripHtml("no html here"))
    }

    @Test
    fun mapContent_htmlContentBecomesPlainText() {
        val mapper = mapper()
        val content = mapper.mapContent(status(content = "<p>Hello <strong>world</strong></p>"))
        assertEquals("Hello world", content.text)
    }

    // ------------------------------------------------------------------
    // parentRef (in_reply_to_id)
    // ------------------------------------------------------------------

    @Test
    fun mapContent_mapsInReplyToIdToParentRef() {
        val mapper = mapper()
        val content = mapper.mapContent(status(inReplyToId = "900"))
        assertEquals("900", content.parentRef)
    }

    // ------------------------------------------------------------------
    // Reblog -> repostOfRef (no recursion)
    // ------------------------------------------------------------------

    @Test
    fun mapContent_reblogMapsRepostOfRefWithoutRecursion() {
        val mapper = mapper()
        val original = status(id = "777", content = "original text")
        val boost = status(id = "888", reblog = original)

        val content = mapper.mapContent(boost)
        // The boost status id is the content id; the reblog target is the repost ref.
        assertEquals("888", content.sourceContentId)
        assertEquals("777", content.repostOfRef)
        // original text is surfaced via the reblog wrapper content
        assertEquals("original text", content.text)
    }

    @Test
    fun mapContent_noReblogLeavesRepostOfRefNull() {
        val mapper = mapper()
        val content = mapper.mapContent(status(id = "555"))
        assertNull(content.repostOfRef)
    }

    // ------------------------------------------------------------------
    // Quote -> quoteOfRef
    // ------------------------------------------------------------------

    @Test
    fun mapContent_quoteMapsQuoteOfRef() {
        val mapper = mapper()
        val quoted = status(id = "333", content = "quoted text")
        val quoting = status(id = "444", quote = MastodonQuote(state = "accepted", quoted_status = quoted))

        val content = mapper.mapContent(quoting)
        assertEquals("333", content.quoteOfRef)
    }

    @Test
    fun mapContent_shallowQuoteMapsQuotedStatusId() {
        val mapper = mapper()
        val quoting = status(id = "444", quote = MastodonQuote(state = "accepted", quoted_status_id = "555"))

        val content = mapper.mapContent(quoting)
        assertEquals("555", content.quoteOfRef)
    }

    @Test
    fun mapContent_quoteWithUnknownStateAndNoIdsLeavesQuoteOfRefNull() {
        val mapper = mapper()
        val quoting = status(id = "444", quote = MastodonQuote(state = "some_future_state"))

        val content = mapper.mapContent(quoting)
        assertNull(content.quoteOfRef)
    }

    @Test
    fun mapContent_noQuoteLeavesQuoteOfRefNull() {
        val mapper = mapper()
        val content = mapper.mapContent(status(id = "999"))
        assertNull(content.quoteOfRef)
    }

    // ------------------------------------------------------------------
    // Media mapping
    // ------------------------------------------------------------------

    @Test
    fun mapMedia_mapsAttachmentFields() {
        val mapper = mapper()
        val attach = MastodonMediaAttachment(
            id = "media-1",
            url = "https://m.social/orig.png",
            preview_url = "https://m.social/prev.png",
            remote_url = "https://cdn.example.com/orig.png",
            type = "image",
            meta = MastodonMediaMeta(width = 1600, height = 900)
        )

        val media = mapper.mapMedia(attach)
        assertEquals("https://m.social/orig.png", media.sourceUrl)
        assertEquals("https://m.social/prev.png", media.previewUrl)
        assertEquals("https://cdn.example.com/orig.png", media.originalUrl)
        assertEquals("image/*", media.mimeType)
        assertEquals(1600, media.width)
        assertEquals(900, media.height)
        assertNull(media.sha256)
    }

    @Test
    fun mapPage_mediaFromContentPopulatesMediaInputs() {
        val mapper = mapper()
        val s = MastodonStatus(
            id = "1",
            account = MastodonAccount(id = "a", username = "u", acct = "u"),
            content = "x",
            created_at = "2026-09-08T10:00:00.000Z",
            media_attachments = listOf(
                MastodonMediaAttachment(id = "m1", url = "https://x/1.png", type = "image")
            )
        )
        val result = mapper.mapPage(listOf(s), null)

        assertEquals(listOf("m1"), result.contents[0].mediaRefs)
        assertEquals(1, result.media.size)
        // The media correlation survives: the map key equals the content mediaRef.
        assertTrue(result.media.containsKey("m1"), "media must be keyed by the external media reference")
        val mediaInput = result.media.getValue("m1")
        assertEquals("https://x/1.png", mediaInput.sourceUrl)
        // No Media PublicId is ever produced — media inputs carry no id field by design.
        assertTrue(mediaInput.javaClass.declaredFields.none { it.name == "id" || it.name.contains("publicId") })
    }

    // ------------------------------------------------------------------
    // Visibility mapping
    // ------------------------------------------------------------------

    @Test
    fun mapVisibility_mapsAllMastodonValues() {
        val mapper = mapper()
        assertEquals(ContentVisibility.PUBLIC, mapper.mapContent(status(visibility = "public")).visibility)
        assertEquals(ContentVisibility.UNLISTED, mapper.mapContent(status(visibility = "unlisted")).visibility)
        assertEquals(ContentVisibility.FOLLOWERS, mapper.mapContent(status(visibility = "private")).visibility)
        assertEquals(ContentVisibility.PRIVATE, mapper.mapContent(status(visibility = "direct")).visibility)
        assertEquals(ContentVisibility.UNKNOWN, mapper.mapContent(status(visibility = "weird")).visibility)
    }

    // ------------------------------------------------------------------
    // Link header -> nextCursor
    // ------------------------------------------------------------------

    @Test
    fun parseNextCursor_extractsMaxIdFromNextLink() {
        val mapper = mapper()
        val link = "<https://m.social/api/v1/timelines/public?max_id=123>; rel=\"next\""
        assertEquals("123", mapper.parseNextCursor(link))
    }

    @Test
    fun parseNextCursor_noLinkReturnsNull() {
        val mapper = mapper()
        assertNull(mapper.parseNextCursor(null))
    }

    @Test
    fun parseNextCursor_ignoresPrevAndNoNext() {
        val mapper = mapper()
        val prevOnly = "<https://m.social/api/v1/timelines/public?max_id=0>; rel=\"prev\""
        assertNull(mapper.parseNextCursor(prevOnly))
        // A next link whose URL has no max_id param cannot produce a safe cursor.
        val noMaxId = "<https://m.social/api/v1/timelines/public>; rel=\"next\""
        assertNull(mapper.parseNextCursor(noMaxId))
    }

    // ------------------------------------------------------------------
    // Tags are mapped only when present in the same response
    // ------------------------------------------------------------------

    @Test
    fun mapPage_hashtagsBecomePlatformTagInputs() {
        val mapper = mapper()
        val s = MastodonStatus(
            id = "1",
            account = MastodonAccount(id = "a", username = "u", acct = "u"),
            content = "x",
            created_at = "2026-09-08T10:00:00.000Z",
            tags = listOf(MastodonTag(name = "art"), MastodonTag(name = "kotlin"))
        )
        val result: ExternalFetchResult = mapper.mapPage(listOf(s), null)
        assertEquals(2, result.tags.size)
        assertEquals("art", result.tags[0].name)
    }

    // ------------------------------------------------------------------
    // Content-level tags (Stage 15-B): each content carries ONLY its own tags
    // ------------------------------------------------------------------

    @Test
    fun mapContent_statusHashtagsBecomeThatContentsTagFacts() {
        val mapper = mapper()

        val content = mapper.mapContent(
            status(id = "1", tags = listOf(MastodonTag(name = "alpha"), MastodonTag(name = "beta")))
        )

        assertEquals(2, content.tags.size, "the content must carry its own hashtags as tag facts")
        assertEquals(listOf("alpha", "beta"), content.tags.map { it.name })
        // Mastodon hashtags have no sourceTagId in this stage: they stay in the
        // PLATFORM tag shape (identity = verbatim name), never EXTERNAL, and
        // they still belong to the fetched instance context.
        content.tags.forEach { tag ->
            assertNull(tag.sourceTagId, "Mastodon hashtags must map as PLATFORM tags (sourceTagId = null)")
            assertNull(tag.sourceIdRef, "Mastodon hashtags carry no EXTERNAL source reference")
            assertEquals("mastodon.social", tag.instanceRef.instanceDomain)
        }
    }

    @Test
    fun mapPage_contentsWithDifferentHashtags_neverLeakTagsAcrossContents() {
        val mapper = mapper()
        val statusA = status(id = "A", tags = listOf(MastodonTag(name = "alpha"), MastodonTag(name = "beta")))
        val statusB = status(id = "B", tags = listOf(MastodonTag(name = "gamma")))

        val result = mapper.mapPage(listOf(statusA, statusB), null)

        val contentA = result.contents.single { it.sourceContentId == "A" }
        val contentB = result.contents.single { it.sourceContentId == "B" }
        assertEquals(
            listOf("alpha", "beta"),
            contentA.tags.map { it.name },
            "content A must carry exactly alpha + beta"
        )
        assertEquals(
            listOf("gamma"),
            contentB.tags.map { it.name },
            "content B must carry exactly gamma"
        )
        // The batch-wide flattening failure mode is explicitly forbidden:
        // neither content may receive tags from the other status.
        assertTrue(
            contentA.tags.none { it.name == "gamma" },
            "content A must never receive status B's tags (no batch-wide tag leakage)"
        )
        assertTrue(
            contentB.tags.none { it.name == "alpha" || it.name == "beta" },
            "content B must never receive status A's tags (no batch-wide tag leakage)"
        )
        // The top-level ExternalFetchResult.tags map is still the deduplicated
        // batch view for the author/tag ensure pass; content tags are separate.
        assertEquals(setOf("alpha", "beta", "gamma"), result.tags.map { it.name }.toSet())
    }

    @Test
    fun mapContent_statusWithoutHashtags_carriesEmptyTags() {
        val mapper = mapper()

        val content = mapper.mapContent(status(id = "plain"))

        assertTrue(content.tags.isEmpty(), "a status without hashtags must carry no tag facts")
    }
}

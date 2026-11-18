package com.contentplatform.backend.adapter.mastodon

import com.contentplatform.backend.domain.source.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Mastodon 4.4+ JSON compatibility tests (P8-G).
 *
 * These tests exercise the REAL production binding path
 * ([MastodonJson.parseStatuses] -> [MastodonMapper]), not field-presence checks.
 *
 * They pin the fixes for the P8-F production failure:
 *  - `Status.quote` is a Mastodon 4.4+ Quote / ShallowQuote entity, not a Status;
 *  - the Kotlin module is registered, so absent defaulted constructor params use
 *    their Kotlin defaults instead of failing the whole page.
 */
class MastodonJsonCompatibilityTest {

    private fun mapper() = MastodonMapper(
        sourceType = SourceType.MASTODON,
        instanceDomain = "mstdn.social"
    )

    private fun parseOne(json: String): MastodonStatus {
        val list = MastodonJson.parseStatuses("[$json]")
        assertEquals(1, list.size, "one status must parse")
        return list[0]
    }

    private val account = """"account":{"id":"9","username":"a","acct":"a@x","display_name":"A","avatar":"https://x/a.png","note":"<p>n</p>","url":"https://x/@a"}"""

    // ------------------------------------------------------------------
    // 1. Existing baseline Status
    // ------------------------------------------------------------------

    @Test
    fun `baseline status parses and maps`() {
        val s = parseOne(
            """{"id":"1",$account,"content":"<p>hi</p>","created_at":"2026-09-08T10:00:00.000Z","visibility":"public","language":"en","in_reply_to_id":null,"reblog":null,"media_attachments":[],"tags":[]}"""
        )
        val content = mapper().mapContent(s)
        assertEquals("1", content.sourceContentId)
        assertEquals("9", content.authorRef)
        assertEquals("hi", content.text)
        assertEquals("en", content.language)
    }

    // ------------------------------------------------------------------
    // 2. quote: null
    // ------------------------------------------------------------------

    @Test
    fun `quote null parses and leaves quoteOfRef null`() {
        val s = parseOne("""{"id":"2",$account,"content":"x","quote":null}""")
        assertNull(s.quote)
        assertNull(mapper().mapContent(s).quoteOfRef)
    }

    // ------------------------------------------------------------------
    // 3. quote as full Quote with quoted_status
    // ------------------------------------------------------------------

    @Test
    fun `full quote entity parses and maps quoted_status id`() {
        val s = parseOne(
            """{"id":"3",$account,"content":"x","quote":{"state":"accepted","quoted_status":{"id":"777","account":{"id":"8","username":"b","acct":"b"},"content":"q"}}}"""
        )
        assertNotNull(s.quote)
        assertEquals("accepted", s.quote.state)
        assertEquals("777", s.quote.quoted_status?.id)
        assertEquals("777", mapper().mapContent(s).quoteOfRef)
    }

    // ------------------------------------------------------------------
    // 4. quote as ShallowQuote with quoted_status_id
    // ------------------------------------------------------------------

    @Test
    fun `shallow quote entity parses and maps quoted_status_id`() {
        val s = parseOne(
            """{"id":"4",$account,"content":"x","quote":{"state":"accepted","quoted_status_id":"888"}}"""
        )
        assertEquals("888", s.quote!!.quoted_status_id)
        assertEquals("888", mapper().mapContent(s).quoteOfRef)
    }

    // ------------------------------------------------------------------
    // 5. quote state unknown
    // ------------------------------------------------------------------

    @Test
    fun `unknown quote state does not fail parsing or mapping`() {
        val s = parseOne(
            """{"id":"5",$account,"content":"x","quote":{"state":"some_future_state","quoted_status_id":"999"}}"""
        )
        assertEquals("some_future_state", s.quote!!.state)
        assertEquals("999", mapper().mapContent(s).quoteOfRef)
    }

    // ------------------------------------------------------------------
    // 6. quote with quoted_status = null
    // ------------------------------------------------------------------

    @Test
    fun `quote with null quoted_status and null quoted_status_id maps to null`() {
        val s = parseOne(
            """{"id":"6",$account,"content":"x","quote":{"state":"pending","quoted_status":null,"quoted_status_id":null}}"""
        )
        assertNull(s.quote!!.quoted_status)
        assertNull(mapper().mapContent(s).quoteOfRef)
    }

    // ------------------------------------------------------------------
    // 7. existing reblog nested Status
    // ------------------------------------------------------------------

    @Test
    fun `reblog nested status still parses and maps repostOfRef`() {
        val s = parseOne(
            """{"id":"7",$account,"content":"wrapper","reblog":{"id":"71","account":{"id":"8","username":"b","acct":"b"},"content":"orig"}}"""
        )
        val content = mapper().mapContent(s)
        assertEquals("7", content.sourceContentId)
        assertEquals("71", content.repostOfRef)
        assertEquals("orig", content.text)
    }

    // ------------------------------------------------------------------
    // 8. media / tags
    // ------------------------------------------------------------------

    @Test
    fun `media and tags parse and map`() {
        val s = parseOne(
            """{"id":"8",$account,"content":"x","media_attachments":[{"id":"m1","url":"https://x/o.png","preview_url":"https://x/p.png","type":"image","meta":{"width":10,"height":20}}],"tags":[{"name":"art","url":"https://x/tags/art"}]}"""
        )
        val content = mapper().mapContent(s)
        assertEquals(listOf("m1"), content.mediaRefs)
        assertEquals(listOf("art"), content.tags.map { it.name })
    }

    // ------------------------------------------------------------------
    // 9. missing optional / defaulted Kotlin fields
    // ------------------------------------------------------------------

    @Test
    fun `missing defaulted fields are filled from Kotlin defaults instead of failing`() {
        // top-level id absent, account.acct absent, quote has no ids
        val s = parseOne("""{"account":{"id":"9","username":"a"},"content":"x","quote":{"state":"accepted"}}""")
        assertEquals("", s.id)
        assertEquals("", s.account?.acct)
        assertEquals("", mapper().mapContent(s).sourceContentId)
        assertNull(mapper().mapContent(s).quoteOfRef)
    }

    // ------------------------------------------------------------------
    // 10. unknown additional Mastodon fields
    // ------------------------------------------------------------------

    @Test
    fun `unknown additional fields are ignored`() {
        val s = parseOne(
            """{"id":"9",$account,"content":"x","some_future_field":{"nested":[1,2,3]},"quote":{"state":"accepted","quoted_status_id":"111","extra":"y"}}"""
        )
        assertEquals("111", mapper().mapContent(s).quoteOfRef)
    }

    // ------------------------------------------------------------------
    // Real captured mstdn.social payload (Mastodon 4.7, quote entity)
    // ------------------------------------------------------------------

    @Test
    fun `real mstdn_social status containing a quote entity parses`() {
        // Trimmed, verbatim shape captured from
        // GET https://mstdn.social/api/v1/timelines/public (anonymous, P8-F).
        val real = """
            {
              "id": "117266488389654159",
              "created_at": "2026-09-14T00:10:07.000Z",
              "content": "<p class=\"quote-inline\">RE: <a href=\"https://mstdn.jp/@mapleleaf240/117266448358990904\">x</a></p><p>のらたん</p>",
              "visibility": "public",
              "account": {"id": "101581", "username": "whtapple", "acct": "whtapple@ippandon.hopto.org",
                          "display_name": "白林檎美和", "avatar": "https://media.mstdn.social/cache/accounts/avatars/x.jpg",
                          "note": "<p>bio</p>", "url": "https://ippandon.hopto.org/@whtapple"},
              "url": "https://ippandon.hopto.org/@whtapple/117266488220659388",
              "language": "ja",
              "in_reply_to_id": null,
              "media_attachments": [],
              "tags": [],
              "reblog": null,
              "quote": {"state": "accepted", "quoted_status": {"id": "117266448411317367"}, "quoted_status_id": null}
            }
        """.trimIndent()

        val s = parseOne(real)
        assertEquals("117266488389654159", s.id)
        assertEquals("accepted", s.quote!!.state)
        assertEquals("117266448411317367", mapper().mapContent(s).quoteOfRef)
    }

    @Test
    fun `a full page mixing quote shapes parses without failing`() {
        val page = """
            [
              {"id":"1",$account,"content":"a","quote":null},
              {"id":"2",$account,"content":"b","quote":{"state":"accepted","quoted_status":{"id":"21","account":{"id":"8","username":"b","acct":"b"}}}},
              {"id":"3",$account,"content":"c","quote":{"state":"accepted","quoted_status_id":"31"}},
              {"id":"4",$account,"content":"d","quote":{"state":"rejected","quoted_status":null}},
              {"id":"5",$account,"content":"e"}
            ]
        """.trimIndent()

        val statuses = MastodonJson.parseStatuses(page)
        val result = mapper().mapPage(statuses, linkHeader = null)

        assertEquals(5, result.contents.size)
        assertEquals(
            listOf(null, "21", "31", null, null),
            result.contents.map { it.quoteOfRef }
        )
    }
}

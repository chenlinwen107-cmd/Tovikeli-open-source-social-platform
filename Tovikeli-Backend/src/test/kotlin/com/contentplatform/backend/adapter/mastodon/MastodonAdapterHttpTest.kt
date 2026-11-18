package com.contentplatform.backend.adapter.mastodon

import com.contentplatform.backend.adapter.AdapterException
import com.contentplatform.backend.adapter.ExternalAdapter
import com.contentplatform.backend.adapter.FetchCommand
import com.contentplatform.backend.domain.source.SourceType
import com.sun.net.httpserver.HttpServer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress

/**
 * HTTP-boundary tests for MastodonAdapter using a JDK in-process HttpServer
 * stub (no WireMock / no external dependency / no real network).
 */
class MastodonAdapterHttpTest {

    private lateinit var server: HttpServer
    private var port: Int = 0
    private var lastPath: String = ""

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        port = server.address.port
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    private fun stub(responseBody: String, status: Int = 200, linkHeader: String? = null) {
        server.createContext("/api/v1/timelines/public") { exchange ->
            lastPath = exchange.requestURI.toString()
            if (linkHeader != null) exchange.responseHeaders.add("Link", linkHeader)
            val bytes = responseBody.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun adapter(): ExternalAdapter = MastodonAdapter(
        restClient = RestClient.builder()
            .requestFactory(JdkClientHttpRequestFactory())
            .build(),
        uriScheme = "http"
    )

    private fun fetchCommand(cursor: String? = null, since: Long? = null) = FetchCommand(
        sourceType = SourceType.MASTODON,
        instanceDomain = "127.0.0.1:$port",
        cursor = cursor,
        since = since
    )

    // ------------------------------------------------------------------
    // Link header -> nextCursor via real HTTP
    // ------------------------------------------------------------------

    @Test
    fun fetch_linkHeaderNextProducesNextCursor() {
        val body = """[{"id":"1","account":{"id":"9","username":"a","acct":"a"},"content":"x","created_at":"2026-09-08T10:00:00.000Z"}]"""
        stub(body, linkHeader = """<http://127.0.0.1:$port/api/v1/timelines/public?max_id=123>; rel="next"""")

        val result = adapter().fetch(fetchCommand())

        assertEquals("123", result.nextCursor)
    }

    @Test
    fun fetch_cursorAndSinceMapToQueryParams() {
        stub("[]")
        adapter().fetch(fetchCommand(cursor = "555", since = 1000L))

        assertTrue(lastPath.contains("max_id=555"), "path=$lastPath")
        assertTrue(lastPath.contains("since_id=1000"), "path=$lastPath")
    }

    @Test
    fun fetch_noNextLinkLeavesNextCursorNull() {
        stub("[]")
        val result = adapter().fetch(fetchCommand())
        assertNull(result.nextCursor)
    }

    // ------------------------------------------------------------------
    // Full slice: real HTTP -> ExternalFetchResult with content/media
    // ------------------------------------------------------------------

    @Test
    fun fetch_realStubMapsFullStatus() {
        val body = """
            [{
              "id": "1001",
              "account": {"id": "9", "username": "alice", "acct": "alice",
                          "display_name": "Alice", "avatar": "https://x/av.jpg",
                          "note": "<p>hi</p>", "url": "https://x/@alice"},
              "content": "<p>Hello <strong>world</strong></p>",
              "created_at": "2026-09-08T10:00:00.000Z",
              "visibility": "public",
              "language": "en",
              "in_reply_to_id": "900",
              "media_attachments": [
                 {"id": "m1", "url": "https://x/o.png", "preview_url": "https://x/p.png",
                  "type": "image", "meta": {"width": 1600, "height": 900}}
              ]
            }]
        """.trimIndent()
        stub(body)

        val result = adapter().fetch(fetchCommand())

        assertEquals(1, result.contents.size)
        val c = result.contents[0]
        assertEquals("1001", c.sourceContentId)
        assertEquals("9", c.authorRef)
        assertEquals("Hello world", c.text)
        assertEquals("en", c.language)
        assertEquals("900", c.parentRef)
        assertEquals(listOf("m1"), c.mediaRefs)
        assertEquals(1, result.authors.size)
        assertEquals(1, result.media.size)
        // media are correlated by their external attachment id (= the mediaRef)
        assertEquals("https://x/p.png", result.media.getValue("m1").previewUrl)
        assertEquals(1600, result.media.getValue("m1").width)
    }

    // ------------------------------------------------------------------
    // HTTP error -> AdapterException
    // ------------------------------------------------------------------

    @Test
    fun fetch_http500BecomesAdapterException() {
        stub("server error", status = 500)
        val e = assertFailsWith<AdapterException> { adapter().fetch(fetchCommand()) }
        assertTrue(e.message!!.contains("500"), "message=${e.message}")
    }

    @Test
    fun fetch_http429BecomesAdapterException() {
        stub("rate limited", status = 429)
        val e = assertFailsWith<AdapterException> { adapter().fetch(fetchCommand()) }
        assertTrue(e.message!!.contains("429"), "message=${e.message}")
    }

    @Test
    fun fetch_http404BecomesAdapterException() {
        stub("not found", status = 404)
        val e = assertFailsWith<AdapterException> { adapter().fetch(fetchCommand()) }
        assertTrue(e.message!!.contains("404"), "message=${e.message}")
    }

    // ------------------------------------------------------------------
    // Malformed JSON -> AdapterException
    // ------------------------------------------------------------------

    @Test
    fun fetch_malformedJsonBecomesAdapterException() {
        stub("[{\"id\": broken")
        val e = assertFailsWith<AdapterException> { adapter().fetch(fetchCommand()) }
        assertTrue(e.message!!.contains("parse"), "message=${e.message}")
    }

    @Test
    fun fetch_wrongSourceTypeRejected() {
        val cmd = FetchCommand(sourceType = SourceType.MISSKEY, instanceDomain = "x")
        assertFailsWith<AdapterException> { adapter().fetch(cmd) }
    }
}

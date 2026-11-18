package com.contentplatform.backend.adapter.misskey

import com.contentplatform.backend.adapter.AdapterException
import com.contentplatform.backend.adapter.ExternalAdapter
import com.contentplatform.backend.adapter.FetchCommand
import com.contentplatform.backend.domain.content.ContentVisibility
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
 * HTTP-boundary tests for [MisskeyAdapter] using a JDK in-process HttpServer
 * stub (no WireMock / no external dependency / no real network).
 */
class MisskeyAdapterHttpTest {

    private lateinit var server: HttpServer
    private var port: Int = 0
    private var lastMethod: String = ""
    private var lastPath: String = ""
    private var lastBody: String = ""

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

    private fun stub(responseBody: String, status: Int = 200) {
        server.createContext("/api/notes/local-timeline") { exchange ->
            lastMethod = exchange.requestMethod
            lastPath = exchange.requestURI.toString()
            lastBody = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val bytes = responseBody.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun adapter(): ExternalAdapter = MisskeyAdapter(
        restClient = RestClient.builder()
            .requestFactory(JdkClientHttpRequestFactory())
            .build(),
        uriScheme = "http"
    )

    private fun fetchCommand(cursor: String? = null) = FetchCommand(
        sourceType = SourceType.MISSKEY,
        instanceDomain = "127.0.0.1:$port",
        cursor = cursor
    )

    // ------------------------------------------------------------------
    // Request shape
    // ------------------------------------------------------------------

    @Test
    fun fetch_postsToLocalTimelineWithFiles() {
        stub("[]")
        adapter().fetch(fetchCommand())

        assertEquals("POST", lastMethod)
        assertTrue(lastPath.contains("/api/notes/local-timeline"), "path=$lastPath")
        assertTrue(lastBody.contains("\"withFiles\":true"), "body=$lastBody")
        assertTrue(lastBody.contains("\"limit\":"), "body=$lastBody")
        assertTrue(!lastBody.contains("untilId"), "body=$lastBody")
    }

    @Test
    fun fetch_cursorMapsToUntilIdWithoutFollowingPagination() {
        stub("[]")
        val result = adapter().fetch(fetchCommand(cursor = "abc"))

        assertTrue(lastBody.contains("\"untilId\":\"abc\""), "body=$lastBody")
        assertNull(result.nextCursor, "the adapter never follows the cursor")
    }

    // ------------------------------------------------------------------
    // Full slice: real HTTP -> ExternalFetchResult
    // ------------------------------------------------------------------

    @Test
    fun fetch_realStubMapsNoteWithMedia() {
        val body = """
            [{
              "id": "n1",
              "text": "hello",
              "createdAt": "2026-09-08T10:00:00.000Z",
              "user": {"id": "u1", "username": "alice", "name": "Alice",
                       "avatarUrl": "https://misskey.art/av.png"},
              "visibility": "public",
              "lang": "en",
              "tags": ["art"],
              "sensitive": false,
              "files": [
                {"id": "f1", "type": "image/png",
                 "url": "https://misskey.art/f1.png",
                 "thumbnailUrl": "https://misskey.art/f1.thumb.png",
                 "isSensitive": false, "comment": "alt",
                 "properties": {"width": 1600, "height": 900}}
              ]
            }]
        """.trimIndent()
        stub(body)

        val result = adapter().fetch(fetchCommand())

        assertEquals(1, result.contents.size)
        val content = result.contents[0]
        assertEquals("n1", content.sourceContentId)
        assertEquals("u1", content.authorRef)
        assertEquals("hello", content.text)
        assertEquals(ContentVisibility.PUBLIC, content.visibility)
        assertEquals(listOf("f1"), content.mediaRefs)

        assertEquals(1, result.authors.size)
        assertEquals(1, result.media.size)
        assertEquals("https://misskey.art/f1.thumb.png", result.media.getValue("f1").previewUrl)
        assertEquals(1600, result.media.getValue("f1").width)
    }

    @Test
    fun fetch_sensitiveAndCwNotesAreFilteredOverHttp() {
        val body = """
            [
              {"id": "n1", "text": "secret", "sensitive": true,
               "user": {"id": "u1", "username": "alice"}},
              {"id": "n2", "text": "warning", "cw": "spoiler",
               "user": {"id": "u2", "username": "bob"}},
              {"id": "n3", "text": "ok", "cw": null, "sensitive": false,
               "user": {"id": "u3", "username": "carol"}}
            ]
        """.trimIndent()
        stub(body)

        val result = adapter().fetch(fetchCommand())

        assertEquals(1, result.contents.size)
        assertEquals("n3", result.contents[0].sourceContentId)
    }

    // ------------------------------------------------------------------
    // HTTP / parse errors -> AdapterException
    // ------------------------------------------------------------------

    @Test
    fun fetch_http500BecomesAdapterException() {
        stub("server error", status = 500)
        val e = assertFailsWith<AdapterException> { adapter().fetch(fetchCommand()) }
        assertTrue(e.message!!.contains("500"), "message=${e.message}")
    }

    @Test
    fun fetch_malformedJsonBecomesAdapterException() {
        stub("[{\"id\": broken")
        val e = assertFailsWith<AdapterException> { adapter().fetch(fetchCommand()) }
        assertTrue(e.message!!.contains("parse"), "message=${e.message}")
    }

    @Test
    fun fetch_wrongSourceTypeRejected() {
        val cmd = FetchCommand(sourceType = SourceType.MASTODON, instanceDomain = "x")
        assertFailsWith<AdapterException> { adapter().fetch(cmd) }
    }
}

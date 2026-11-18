package com.contentplatform.backend.adapter

import com.contentplatform.backend.adapter.mastodon.MastodonAdapter
import com.contentplatform.backend.adapter.misskey.MisskeyAdapter
import com.contentplatform.backend.domain.source.SourceType
import com.sun.net.httpserver.HttpServer
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress

/**
 * Dispatch tests for [ExternalAdapterRouter] (P9-F).
 *
 * The router holds real adapters pointed at a JDK in-process stub; the observed
 * request path proves which concrete adapter handled the command. No network.
 */
class ExternalAdapterRouterTest {

    private lateinit var server: HttpServer
    private var port: Int = 0
    private var lastPath: String = ""

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        port = server.address.port
        server.createContext("/api/v1/timelines/public") { exchange ->
            lastPath = exchange.requestURI.toString()
            respond(exchange, "[]")
        }
        server.createContext("/api/notes/local-timeline") { exchange ->
            lastPath = exchange.requestURI.toString()
            respond(exchange, "[]")
        }
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    private fun respond(exchange: com.sun.net.httpserver.HttpExchange, body: String) {
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun router(): ExternalAdapterRouter {
        val restClient = RestClient.builder()
            .requestFactory(JdkClientHttpRequestFactory())
            .build()
        return ExternalAdapterRouter(
            mastodonAdapter = MastodonAdapter(restClient, uriScheme = "http"),
            misskeyAdapter = MisskeyAdapter(restClient, uriScheme = "http")
        )
    }

    private fun command(sourceType: SourceType) = FetchCommand(
        sourceType = sourceType,
        instanceDomain = "127.0.0.1:$port"
    )

    @Test
    fun fetch_mastodonDispatchesToMastodonAdapter() {
        val result = router().fetch(command(SourceType.MASTODON))
        assertTrue(lastPath.contains("/api/v1/timelines/public"), "path=$lastPath")
        assertTrue(result.contents.isEmpty())
    }

    @Test
    fun fetch_misskeyDispatchesToMisskeyAdapter() {
        val result = router().fetch(command(SourceType.MISSKEY))
        assertTrue(lastPath.contains("/api/notes/local-timeline"), "path=$lastPath")
        assertTrue(result.contents.isEmpty())
    }

    @Test
    fun fetch_unsupportedSourceTypeFailsClosedWithoutRequest() {
        val e = assertFailsWith<AdapterException> {
            router().fetch(command(SourceType.PIXELFED))
        }
        assertTrue(e.message!!.contains("PIXELFED"), "message=${e.message}")
        assertTrue(lastPath.isEmpty(), "no adapter must be called for an unsupported type")
    }
}

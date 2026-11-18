package com.contentplatform.backend.adapter.misskey

import com.contentplatform.backend.adapter.AdapterException
import com.contentplatform.backend.adapter.ExternalAdapter
import com.contentplatform.backend.adapter.ExternalFetchResult
import com.contentplatform.backend.adapter.FetchCommand
import com.contentplatform.backend.domain.source.SourceType
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/**
 * Misskey external adapter — second real platform Vertical Slice (P9-F).
 *
 * HTTP boundary: all Misskey HTTP/JSON handling is encapsulated in this
 * package. The Application only ever sees [ExternalFetchResult] /
 * [AdapterException].
 *
 * Endpoint: POST /api/notes/local-timeline (one finite page; no crawler /
 * scheduler / pagination loop).
 *
 * Request body (V1):
 *  - limit:     bounded page size
 *  - withFiles: true so file facts are present for media correlation
 *  - untilId:   only when FetchCommand.cursor is supplied (accepted by design,
 *               never followed: nextCursor is always null — no pagination loop)
 *
 * The adapter does NOT:
 *  - touch repositories, Domain aggregates, or DB PublicIds
 *  - evaluate Source/Instance policy (enabled / allowIndex / healthStatus)
 *  - generate any Media PublicId
 *  - perform authentication (public local timeline only)
 *
 * [uriScheme] defaults to "https" and is overridable only for local HTTP
 * testing against a stub server (never used for production instances).
 *
 * @throws AdapterException for HTTP errors, malformed JSON, or unparseable
 *         platform payloads. No retry / backoff is performed.
 */
class MisskeyAdapter(
    private val restClient: RestClient,
    private val uriScheme: String = "https",
    private val pageLimit: Int = DEFAULT_PAGE_LIMIT
) : ExternalAdapter {

    override fun fetch(command: FetchCommand): ExternalFetchResult {
        if (command.sourceType != SourceType.MISSKEY) {
            throw AdapterException("MisskeyAdapter received sourceType=${command.sourceType}; expected MISSKEY")
        }
        val instanceDomain = command.instanceDomain
            ?: throw AdapterException("FetchCommand.instanceDomain is required for Misskey fetch")

        val mapper = MisskeyMapper(instanceDomain = instanceDomain)
        val uri = buildTimelineUri(instanceDomain)
        val requestBody = buildRequestBody(command)

        val responseEntity: org.springframework.http.ResponseEntity<String> = try {
            restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .onStatus({ it.isError }) { _, res ->
                    throw AdapterException("Misskey API error: HTTP ${res.statusCode.value()} for POST $uri")
                }
                .toEntity(String::class.java)
        } catch (e: AdapterException) {
            throw e
        } catch (e: Exception) {
            throw AdapterException("Misskey fetch failed for POST $uri", e)
        }

        val notes: List<MisskeyNote> = try {
            MisskeyJson.parseNotes(responseEntity.body.orEmpty())
        } catch (e: Exception) {
            throw AdapterException("Failed to parse Misskey timeline JSON from $uri", e)
        }

        return mapper.mapPage(notes)
    }

    private fun buildTimelineUri(instanceDomain: String): String =
        "$uriScheme://$instanceDomain/api/notes/local-timeline"

    private fun buildRequestBody(command: FetchCommand): Map<String, Any> {
        val body = LinkedHashMap<String, Any>()
        body["limit"] = pageLimit
        body["withFiles"] = true
        // Cursor is accepted so a future caller can request a specific page, but
        // the adapter never follows it (nextCursor stays null).
        command.cursor?.takeIf { it.isNotBlank() }?.let { body["untilId"] = it }
        return body
    }

    private companion object {
        const val DEFAULT_PAGE_LIMIT = 20
    }
}

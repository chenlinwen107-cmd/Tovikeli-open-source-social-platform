package com.contentplatform.backend.adapter.mastodon

import com.contentplatform.backend.adapter.AdapterException
import com.contentplatform.backend.adapter.ExternalAdapter
import com.contentplatform.backend.adapter.ExternalFetchResult
import com.contentplatform.backend.adapter.FetchCommand
import com.contentplatform.backend.domain.source.SourceType
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/**
 * Mastodon external adapter — first real platform Vertical Slice.
 *
 * HTTP boundary: all Mastodon HTTP/JSON handling is encapsulated in this
 * package. The Application only ever sees [ExternalFetchResult] /
 * [AdapterException].
 *
 * Endpoint: GET /api/v1/timelines/public (one finite batch; no crawler /
 * scheduler / pagination loop).
 *
 * Query mapping:
 *  - FetchCommand.cursor -> max_id
 *  - FetchCommand.since  -> since_id
 *  (until is not safely mappable to this endpoint's supported params for the
 *   public timeline; it is intentionally not sent.)
 *
 * The next page cursor is taken from the HTTP Link header (rel="next") inside
 * the adapter; the header never leaves this package.
 *
 * [uriScheme] defaults to "https" and is overridable only for local HTTP
 * testing against a stub server (never used for production instances).
 *
 * The adapter does NOT:
 *  - touch repositories, Domain aggregates, or DB PublicIds
 *  - evaluate Source/Instance policy (enabled / allowIndex / healthStatus)
 *  - generate any Media PublicId
 *  - perform authentication (public timeline only)
 *
 * @throws AdapterException for HTTP errors, malformed JSON, or unparseable
 *         platform payloads. No retry / backoff is performed.
 */
class MastodonAdapter(
    private val restClient: RestClient,
    private val uriScheme: String = "https"
) : ExternalAdapter {

    override fun fetch(command: FetchCommand): ExternalFetchResult {
        if (command.sourceType != SourceType.MASTODON) {
            throw AdapterException("MastodonAdapter received sourceType=${command.sourceType}; expected MASTODON")
        }
        val instanceDomain = command.instanceDomain
            ?: throw AdapterException("FetchCommand.instanceDomain is required for Mastodon fetch")

        val mapper = MastodonMapper(sourceType = command.sourceType, instanceDomain = instanceDomain)
        val uri = buildTimelineUri(instanceDomain, command)

        val responseEntity: org.springframework.http.ResponseEntity<String> = try {
            restClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus({ it.isError }) { _, res ->
                    throw AdapterException("Mastodon API error: HTTP ${res.statusCode.value()} for GET $uri")
                }
                .toEntity(String::class.java)
        } catch (e: AdapterException) {
            throw e
        } catch (e: Exception) {
            throw AdapterException("Mastodon fetch failed for GET $uri", e)
        }

        val linkHeader = responseEntity.headers.getFirst("Link")

        val statuses: List<MastodonStatus> = try {
            MastodonJson.parseStatuses(responseEntity.body.orEmpty())
        } catch (e: Exception) {
            throw AdapterException("Failed to parse Mastodon timeline JSON from $uri", e)
        }

        return mapper.mapPage(statuses, linkHeader)
    }

    private fun buildTimelineUri(instanceDomain: String, command: FetchCommand): String {
        val sb = StringBuilder()
        sb.append(uriScheme).append("://").append(instanceDomain).append("/api/v1/timelines/public")
        val params = mutableListOf<String>()
        command.cursor?.let { params += "max_id=$it" }
        command.since?.let { params += "since_id=$it" }
        if (params.isNotEmpty()) {
            sb.append('?').append(params.joinToString("&"))
        }
        return sb.toString()
    }
}

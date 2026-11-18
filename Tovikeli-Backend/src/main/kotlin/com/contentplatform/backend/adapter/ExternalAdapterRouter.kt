package com.contentplatform.backend.adapter

import com.contentplatform.backend.adapter.mastodon.MastodonAdapter
import com.contentplatform.backend.adapter.misskey.MisskeyAdapter
import com.contentplatform.backend.domain.source.SourceType

/**
 * Explicit, approved P9 multi-platform extension: dispatches an
 * [ExternalAdapter.fetch] call to the concrete platform adapter selected by
 * [FetchCommand.sourceType].
 *
 * Design notes:
 *  - This router was approved as an explicit architectural extension in the P9
 *    productization phase. The V1 stage intentionally had no platform routing;
 *    see the [ExternalAdapter] boundary note.
 *  - The [ExternalAdapter] interface is UNCHANGED: the router itself implements
 *    it and is injected wherever the Application previously received a single
 *    adapter. Use cases stay interface-typed and never know a concrete adapter.
 *  - The concrete adapters are injected EXPLICITLY (never `List<ExternalAdapter>`)
 *    so the router can never inject itself and create a wiring cycle.
 *  - An unsupported source type fails closed with [AdapterException]; no
 *    fallback / default adapter is selected.
 */
class ExternalAdapterRouter(
    private val mastodonAdapter: MastodonAdapter,
    private val misskeyAdapter: MisskeyAdapter
) : ExternalAdapter {

    override fun fetch(command: FetchCommand): ExternalFetchResult =
        when (command.sourceType) {
            SourceType.MASTODON -> mastodonAdapter.fetch(command)
            SourceType.MISSKEY -> misskeyAdapter.fetch(command)
            else -> throw AdapterException(
                "No external adapter registered for sourceType=${command.sourceType}"
            )
        }
}

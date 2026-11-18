package com.contentplatform.backend.adapter

/**
 * Minimal Application-facing contract for fetching platform-independent
 * external data through a concrete adapter.
 *
 * Boundary rules:
 *  - pure Kotlin: no Spring, no HTTP client, no SDK, no Repository, no Entity
 *  - no database PublicId, no DB identifiers, no transaction
 *  - depends only on the 7A [FetchCommand] and returns [ExternalFetchResult]
 *
 * The concrete adapter implementation (platform HTTP/SDK/JSON parsing) must
 * stay behind this interface and is resolved by the Application via normal
 * dependency injection.
 *
 * Platform routing note:
 *  - The V1 stage had a single adapter and intentionally used no
 *    Registry / Factory / SPI.
 *  - The P9 multi-platform extension (explicitly approved) adds
 *    [ExternalAdapterRouter], which is itself an [ExternalAdapter] and selects
 *    the concrete adapter by [FetchCommand.sourceType]. This interface and its
 *    single-method contract are UNCHANGED.
 *
 * Intentionally a SINGLE method: fetch(command). Do not split it into
 * fetchAuthors()/fetchContents()/fetchMedia()/fetchTags().
 */
interface ExternalAdapter {

    /**
     * Fetches one batch of external items for the given fetch command.
     *
     * @param command external fetch instruction (source type + instance domain
     *        + optional cursor/time window) — carries NO database identifiers
     * @return a batch of normalized platform-independent external inputs and an
     *         optional next cursor
     * @throws AdapterException when the external platform cannot be reached or
     *         its response cannot be normalized
     */
    fun fetch(command: FetchCommand): ExternalFetchResult
}

package com.contentplatform.backend.adapter

import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.domain.source.SourceType
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AdapterContractRuntimeTest {

    private val command = FetchCommand(
        sourceType = SourceType.MISSKEY,
        instanceDomain = "misskey.io",
        cursor = "next-1"
    )

    /** Minimal test adapter inline (no extra file). */
    private class RecordingTestAdapter(private val result: ExternalFetchResult) : ExternalAdapter {
        var lastCommand: FetchCommand? = null
            private set

        override fun fetch(command: FetchCommand): ExternalFetchResult {
            lastCommand = command
            return result
        }
    }

    @Test
    fun fetchCommand_flowsThroughAdapterContract() {
        val adapter = RecordingTestAdapter(ExternalFetchResult(nextCursor = "page-2"))

        val result = adapter.fetch(command)

        assertEquals(SourceType.MISSKEY, adapter.lastCommand?.sourceType)
        assertEquals("misskey.io", adapter.lastCommand?.instanceDomain)
        assertEquals("next-1", adapter.lastCommand?.cursor)
        assertEquals("page-2", result.nextCursor)
        assertTrue(result.authors.isEmpty())
        assertTrue(result.contents.isEmpty())
    }

    @Test
    fun externalFetchResult_carriesAllInputKindsAndNextCursor() {
        val result = ExternalFetchResult(
            authors = listOf(
                ExternalAuthorInput(
                    instanceRef = ExternalInstanceRef("misskey.io"),
                    sourceAuthorId = "a1", name = "Alice", username = "alice"
                )
            ),
            contents = listOf(
                ExternalContentInput(
                    instanceRef = ExternalInstanceRef("misskey.io"),
                    sourceContentId = "c1",
                    authorRef = "a1",
                    text = "hi",
                    visibility = ContentVisibility.PUBLIC,
                    mediaRefs = listOf("m1")
                )
            ),
            media = mapOf(
                "m1" to ExternalMediaInput(
                    instanceRef = ExternalInstanceRef("misskey.io"),
                    sourceUrl = "https://example.com/x.png",
                    sha256 = "abc"
                )
            ),
            tags = listOf(
                ExternalTagInput(
                    instanceRef = ExternalInstanceRef("misskey.io"),
                    name = "art",
                    sourceTagId = "t1"
                )
            ),
            nextCursor = "page-3"
        )

        assertEquals(1, result.authors.size)
        assertEquals(1, result.contents.size)
        assertEquals(1, result.media.size)
        assertEquals(1, result.tags.size)
        assertEquals("page-3", result.nextCursor)
        // content/media inputs carried but contain no DB identity
        assertEquals(listOf("m1"), result.contents[0].mediaRefs)
        assertEquals("abc", result.media.getValue("m1").sha256)
        // media correlation: map key is the same external ref used by content.mediaRefs
        assertTrue(result.media.containsKey(result.contents[0].mediaRefs.single()))
    }

    @Test
    fun externalFetchResult_hasNoPublicIdOrDbIdOrSpringField() {
        val fields = ExternalFetchResult::class.memberProperties.map { it.name }.toSet()
        assertTrue(fields.containsAll(setOf("authors", "contents", "media", "tags", "nextCursor")))
        assertTrue(!fields.contains("publicId") && !fields.contains("sourceMediaId"))
        assertNull(ExternalFetchResult().nextCursor)
    }
}

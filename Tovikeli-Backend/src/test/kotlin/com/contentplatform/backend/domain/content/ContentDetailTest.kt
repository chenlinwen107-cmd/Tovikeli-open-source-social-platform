package com.contentplatform.backend.domain.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.interaction.InteractionStatistics
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentDetailTest {

    @Test
    fun construction_holdsAllFields() {
        val content = Content.createFromAdapter(
            instanceIdValue = "inst_1",
            sourceContentId = "sc_1",
            authorId = PublicId("au_1"),
            sourceId = PublicId("src_1"),
            text = "hello",
            language = "en",
            visibility = ContentVisibility.PUBLIC,
            mediaIds = emptyList(),
            parentId = null,
            repostOfId = null,
            quoteOfId = null,
            externalCreatedAt = null,
            metadata = emptyMap()
        )
        val stats = InteractionStatistics.fromCounts(
            mapOf(
                com.contentplatform.backend.domain.interaction.InteractionType.LIKE to 5L
            )
        )
        val detail = ContentDetail(content = content, commentCount = 3L, interactionStatistics = stats)

        assertEquals(content, detail.content)
        assertEquals(3L, detail.commentCount)
        assertEquals(5L, detail.interactionStatistics.like)
        assertEquals(5L, detail.interactionStatistics.total)
    }

    @Test
    fun construction_zeroCounts() {
        val content = Content.createFromAdapter(
            instanceIdValue = "inst_1",
            sourceContentId = "sc_1",
            authorId = PublicId("au_1"),
            sourceId = PublicId("src_1"),
            text = null,
            language = null,
            visibility = ContentVisibility.PUBLIC,
            mediaIds = emptyList(),
            parentId = null,
            repostOfId = null,
            quoteOfId = null,
            externalCreatedAt = null,
            metadata = emptyMap()
        )
        val detail = ContentDetail(content = content, commentCount = 0L, interactionStatistics = InteractionStatistics.ZERO)

        assertEquals(0L, detail.commentCount)
        assertEquals(InteractionStatistics.ZERO, detail.interactionStatistics)
    }
}

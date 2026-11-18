package com.contentplatform.backend.application.feed

import com.contentplatform.backend.domain.feed.FeedType
import com.contentplatform.backend.repository.ContentRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * 第一版推荐：直接使用完整的公开内容池。
 *
 * 当前不做复杂排序算法；推荐可以展示新内容，也可以展示 24 小时
 * 以前的内容。后续如果需要，再在这里增加简单的热度/时间排序。
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class RecommendedFeedUseCase(
    private val contentRepository: ContentRepository
) : FeedUseCase {

    override fun query(request: FeedQuery): FeedResult {
        require(request.type == FeedType.RECOMMEND)

        val page = contentRepository.findPublicContent(
            instanceId = null,
            authorId = null,
            hasMedia = null,
            cursor = request.cursor,
            limit = request.limit,
            viewerAuthorId = request.callerUserId
        )
        return FeedResult.Ready(FeedPage(page.contents, page.nextCursor))
    }
}

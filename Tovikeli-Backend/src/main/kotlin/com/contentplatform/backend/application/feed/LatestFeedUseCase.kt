package com.contentplatform.backend.application.feed

import com.contentplatform.backend.domain.feed.FeedType
import com.contentplatform.backend.repository.ContentRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * "最新"只展示最近 24 小时内发布的公开内容。
 *
 * 推荐频道则使用完整公开内容池，因此两者职责明确：
 *   - latest: 最近 24h
 *   - recommend: 所有可公开展示的内容
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class LatestFeedUseCase(
    private val contentRepository: ContentRepository
) : FeedUseCase {

    companion object {
        private const val RECENT_WINDOW_MILLIS = 24L * 60L * 60L * 1000L
    }

    override fun query(request: FeedQuery): FeedResult {
        require(request.type == FeedType.LATEST)

        val since = System.currentTimeMillis() - RECENT_WINDOW_MILLIS
        val page = contentRepository.findPublicContentSince(
            since = since,
            cursor = request.cursor,
            limit = request.limit,
            viewerUserId = request.callerUserId
        )
        return FeedResult.Ready(FeedPage(page.contents, page.nextCursor))
    }
}

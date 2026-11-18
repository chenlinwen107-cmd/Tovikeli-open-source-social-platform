package com.contentplatform.backend.application.feed

import com.contentplatform.backend.domain.feed.FeedType
import com.contentplatform.backend.repository.ContentRepository
import org.springframework.stereotype.Service

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["spring.datasource.url"])
class VideoFeedUseCase(
    private val contentRepository: ContentRepository
) : FeedUseCase {
    override fun query(request: FeedQuery): FeedResult {
        require(request.type == FeedType.VIDEO)
        val page = contentRepository.findPublicVideoContent(request.cursor, request.limit)
        return FeedResult.Ready(FeedPage(page.contents, page.nextCursor))
    }
}

package com.contentplatform.backend.application.feed

import com.contentplatform.backend.domain.feed.FeedType
import org.springframework.stereotype.Service

@Service
class HomeFeedUseCase(
    private val recommended: RecommendedFeedUseCase,
    private val latest: LatestFeedUseCase,
    private val following: FollowingFeedUseCase,
    private val video: VideoFeedUseCase
) {
    fun query(request: FeedQuery): FeedResult =
        when (request.type) {
            FeedType.RECOMMEND -> recommended.query(request)
            FeedType.LATEST -> latest.query(request)
            FeedType.FOLLOWING -> following.query(request)
            FeedType.VIDEO -> video.query(request)
        }
}

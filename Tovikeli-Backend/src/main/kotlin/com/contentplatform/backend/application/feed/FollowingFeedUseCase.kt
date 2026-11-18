package com.contentplatform.backend.application.feed

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.feed.FeedType
import com.contentplatform.backend.repository.AuthorRepository
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.FollowRepository
import org.springframework.stereotype.Service

@Service
class FollowingFeedUseCase(
    private val followRepository: FollowRepository,
    private val contentRepository: ContentRepository,
    private val authorRepository: AuthorRepository
) : FeedUseCase {
    override fun query(request: FeedQuery): FeedResult {
        require(request.type == FeedType.FOLLOWING)
        val caller = requireNotNull(request.callerUserId) { "following feed requires an authenticated caller" }

        // The relation lookup is deliberately bounded. Content pagination remains
        // cursor-based; this bound prevents an unbounded follow graph from
        // becoming an accidental full-table query.
        val followedUserIds = followRepository.findFollowedUserIds(caller, 5000)
        // Follow rows use platform_user.id, while content.author_id stores the
        // local Author aggregate id. Translate the identity domain before
        // querying ContentRepository; passing platform user ids directly would
        // make the following feed silently return no posts.
        val localAuthorIds = followedUserIds.mapNotNull { userId ->
            authorRepository.findByInstanceAndSourceAuthorId(
                PublicId("inst_local"),
                userId.value
            )?.id
        }
        val page = contentRepository.findPublicContentByAuthors(
            localAuthorIds, request.cursor, request.limit
        )
        return FeedResult.Ready(FeedPage(page.contents, page.nextCursor))
    }
}

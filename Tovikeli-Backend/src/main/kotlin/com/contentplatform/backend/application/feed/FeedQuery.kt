package com.contentplatform.backend.application.feed

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.feed.FeedType
import com.contentplatform.backend.repository.ContentCursor

data class FeedQuery(
    val type: FeedType,
    val callerUserId: PublicId?,
    val cursor: ContentCursor?,
    val limit: Int
)

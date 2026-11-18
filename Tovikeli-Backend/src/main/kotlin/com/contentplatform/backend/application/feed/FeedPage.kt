package com.contentplatform.backend.application.feed

import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.repository.ContentCursor

data class FeedPage(
    val contents: List<Content>,
    val nextCursor: ContentCursor?
)

package com.contentplatform.backend.application.feed

sealed interface FeedResult {
    data class Ready(val page: FeedPage) : FeedResult
    data class NotImplemented(val reason: String) : FeedResult
}

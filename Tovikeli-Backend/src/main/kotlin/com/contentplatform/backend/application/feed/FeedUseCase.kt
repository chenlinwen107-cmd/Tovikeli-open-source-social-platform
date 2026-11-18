package com.contentplatform.backend.application.feed

fun interface FeedUseCase {
    fun query(request: FeedQuery): FeedResult
}

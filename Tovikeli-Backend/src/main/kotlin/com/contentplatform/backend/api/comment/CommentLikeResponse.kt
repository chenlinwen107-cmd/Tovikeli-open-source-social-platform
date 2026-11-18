package com.contentplatform.backend.api.comment

import com.contentplatform.backend.application.comment.CommentLikeState

data class CommentLikeResponse(
    val liked: Boolean,
    val likeCount: Long
) {
    companion object {
        fun from(state: CommentLikeState) = CommentLikeResponse(
            liked = state.liked,
            likeCount = state.likeCount
        )
    }
}

package com.contentplatform.backend.api.content

data class PublishPollRequest(
    val options: List<String> = emptyList(),
    val multi: Boolean = false,
    val days: Int = 0
)

data class PublishContentRequest(
    val text: String? = null,
    val mediaIds: List<String> = emptyList(),
    val poll: PublishPollRequest? = null,
    val location: String? = null,
    val visibility: String? = null,
    val anonymous: Boolean = false
)

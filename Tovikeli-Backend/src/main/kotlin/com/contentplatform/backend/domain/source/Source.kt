package com.contentplatform.backend.domain.source

import com.contentplatform.backend.domain.common.PublicId

data class Source(
    val id: PublicId,
    val name: String,
    val type: SourceType,
    val enabled: Boolean
)

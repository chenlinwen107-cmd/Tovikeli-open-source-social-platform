package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.poll.Poll
import com.contentplatform.backend.domain.poll.PollResult

interface PollRepository {
    fun save(poll: Poll): Poll
    fun findByContentIds(contentIds: List<PublicId>, userId: PublicId?): Map<String, PollResult>
    fun addVotes(contentId: PublicId, userId: PublicId, optionIds: List<PublicId>): PollResult
}

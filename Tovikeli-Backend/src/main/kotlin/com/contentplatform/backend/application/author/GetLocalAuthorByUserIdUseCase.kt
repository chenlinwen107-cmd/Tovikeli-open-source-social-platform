package com.contentplatform.backend.application.author

import com.contentplatform.backend.domain.author.Author
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.AuthorRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class GetLocalAuthorByUserIdUseCase(
    private val authorRepository: AuthorRepository
) {

    companion object {
        private val LOCAL_INSTANCE_ID = PublicId("inst_local")
    }

    fun getByUserId(userId: PublicId): Author? =
        authorRepository.findByInstanceAndSourceAuthorId(
            instanceId = LOCAL_INSTANCE_ID,
            sourceAuthorId = userId.value,
        )
}

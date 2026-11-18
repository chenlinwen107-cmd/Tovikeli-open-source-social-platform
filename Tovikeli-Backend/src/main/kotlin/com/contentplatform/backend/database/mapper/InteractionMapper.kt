package com.contentplatform.backend.database.mapper

import com.contentplatform.backend.database.entity.InteractionEntity
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.interaction.Interaction
import com.contentplatform.backend.domain.interaction.InteractionType

/**
 * Bidirectional mapper between Domain [Interaction] and persistence
 * [InteractionEntity].
 */
object InteractionMapper {

    fun toEntity(interaction: Interaction): InteractionEntity = InteractionEntity(
        id = interaction.id.value,
        userId = interaction.userId.value,
        contentId = interaction.contentId.value,
        type = interaction.type.name,
        createdAt = interaction.createdAt
    )

    fun toDomain(entity: InteractionEntity): Interaction = Interaction(
        id = PublicId(entity.id),
        userId = PublicId(entity.userId),
        contentId = PublicId(entity.contentId),
        type = InteractionType.valueOf(entity.type),
        createdAt = entity.createdAt
    )
}

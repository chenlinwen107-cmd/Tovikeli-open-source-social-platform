package com.contentplatform.backend.application.notification

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.NotificationItem
import com.contentplatform.backend.repository.NotificationRepository
import org.springframework.stereotype.Service

@Service
class NotificationUseCase(private val notifications:NotificationRepository) {
    fun list(userId:PublicId,limit:Int)=notifications.list(userId,limit)
    fun markRead(userId:PublicId,id:Long)=notifications.markRead(userId,id)
    fun markAllRead(userId:PublicId)=notifications.markAllRead(userId)
    fun create(recipientId:PublicId,actorId:PublicId?,type:String,targetType:String="",targetId:String="",preview:String=""):NotificationItem =
        notifications.create(recipientId,actorId,type,targetType,targetId,preview)
}

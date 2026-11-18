package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId

data class NotificationActor(val id:String,val username:String,val nickname:String,val avatar:String?=null)
data class NotificationItem(val id:Long,val type:String,val targetType:String,val targetId:String,val preview:String,val read:Boolean,val createdAt:Long,val actor:NotificationActor?=null)

interface NotificationRepository {
    fun list(recipientId:PublicId,limit:Int):List<NotificationItem>
    fun create(recipientId:PublicId,actorId:PublicId?,type:String,targetType:String="",targetId:String="",preview:String=""):NotificationItem
    fun markRead(recipientId:PublicId,id:Long):Boolean
    fun markAllRead(recipientId:PublicId):Int
}

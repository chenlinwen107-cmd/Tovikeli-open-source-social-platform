package com.contentplatform.backend.application.event

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.*
import com.contentplatform.backend.application.notification.NotificationUseCase
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.UUID

@Service
@ConditionalOnProperty(name=["spring.datasource.url"])
class EventUseCase(private val events: EventRepository, private val notifications: NotificationUseCase) {
    private val categories=setOf("聚会","讲座","运动","桌游","线上","公益")
    fun list(filter:String?,category:String?,viewerId:PublicId?,limit:Int)=events.list(filter,category,viewerId,limit)
    fun detail(id:PublicId,viewerId:PublicId?)=events.findById(id,viewerId)
    fun create(userId:PublicId,title:String,cover:String,location:String,category:String,startAt:Long,endAt:Long,capacity:Int,fee:Double,online:Boolean,description:String):CommunityEvent {
        require(title.trim().length in 2..120){"invalid title"}; require(cover.length<=500){"invalid cover"}; require(location.length<=300){"invalid location"}
        require(category in categories){"invalid category"}; require(endAt>startAt){"invalid time"}; require(startAt>System.currentTimeMillis()){"event must start in the future"}
        require(capacity>=0){"invalid capacity"}; require(fee>=0.0){"invalid fee"}; require(description.length<=5000){"invalid description"}
        return events.create(PublicId(UUID.randomUUID().toString()),userId,title,cover,location,category,startAt,endAt,capacity,fee,online,description,System.currentTimeMillis())
    }
    fun signup(userId:PublicId,id:PublicId):CommunityEvent {
        val current=events.findById(id,userId)?.event?:throw IllegalArgumentException("event not found")
        require(current.status=="upcoming"){"event is not upcoming"}; require(!current.signed){"already signed"}; require(!current.full){"event is full"}
        require(events.signup(id,userId)){"already signed"}; val detail=events.findById(id,userId)!!; if(detail.event.organizer.id!=userId.value) notifications.create(PublicId(detail.event.organizer.id),userId,"event","event",id.value,detail.event.title); return detail.event
    }
    fun cancel(userId:PublicId,id:PublicId):CommunityEvent {
        require(events.findById(id,userId)!=null){"event not found"}; require(events.isSigned(id,userId)){"not signed"}; events.cancelSignup(id,userId); return events.findById(id,userId)!!.event
    }
    fun delete(userId:PublicId,id:PublicId):Boolean { if(!events.isOrganizer(id,userId))throw SecurityException("forbidden"); return events.delete(id) }
}
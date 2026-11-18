package com.contentplatform.backend.application.history

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.BrowseHistoryItem
import com.contentplatform.backend.repository.BrowseHistoryRepository
import org.springframework.stereotype.Service

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["spring.datasource.url"])
class BrowseHistoryUseCase(private val history: BrowseHistoryRepository) {
    fun record(userId:PublicId,type:String,targetId:String,title:String,typeLabel:String,link:String,icon:String,authorNickname:String="",cover:String="",solved:Boolean=false) =
        history.upsert(userId,type,targetId,title,typeLabel,link,icon,authorNickname,cover,solved,System.currentTimeMillis())
    fun list(userId:PublicId,limit:Int=100):List<BrowseHistoryItem> = history.list(userId,limit.coerceIn(1,100))
    fun delete(userId:PublicId,type:String,targetId:String):Boolean = history.delete(userId,type,targetId)
    fun clear(userId:PublicId):Int = history.clear(userId)
}

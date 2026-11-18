package com.contentplatform.backend.application.flash

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.FlashItem
import com.contentplatform.backend.repository.FlashRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.util.UUID

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["spring.datasource.url"])
class FlashUseCase(private val flash: FlashRepository,@Value("\${tovikeli.admin-username:admin}") private val adminUsername: String) {
    private val categories=setOf("公告","功能","活动","精选","教程","动态")
    fun list(category:String?,query:String?,limit:Int)=flash.list(category,query,limit)
    fun create(username:String,title:String,summary:String,category:String,url:String,pinned:Boolean):FlashItem { requireAdmin(username); validate(title,summary,category,url); return flash.create(PublicId(UUID.randomUUID().toString()),title,summary,category,url,pinned,System.currentTimeMillis()) }
    fun update(username:String,id:PublicId,title:String,summary:String,category:String,url:String,pinned:Boolean):FlashItem? { requireAdmin(username); validate(title,summary,category,url); return flash.update(id,title,summary,category,url,pinned,System.currentTimeMillis()) }
    fun delete(username:String,id:PublicId):Boolean { requireAdmin(username); return flash.delete(id) }
    private fun requireAdmin(username:String){ require(username==adminUsername){"administrator access required"} }
    private fun validate(title:String,summary:String,category:String,url:String){ require(title.trim().length in 2..120){"invalid title"}; require(summary.trim().length<=300){"invalid summary"}; require(category in categories){"invalid category"}; require(url.trim().length<=300){"invalid url"}; if(url.isNotBlank()){val u=URI(url.trim()); require(u.scheme=="http"||u.scheme=="https"){"url must use http or https"}; require(!u.host.isNullOrBlank()){"invalid url"}} }
}
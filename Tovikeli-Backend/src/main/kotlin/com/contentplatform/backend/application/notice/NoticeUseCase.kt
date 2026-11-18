package com.contentplatform.backend.application.notice

import com.contentplatform.backend.repository.NoticeRepository
import com.contentplatform.backend.repository.SiteNotice
import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Value
import java.net.URI

@Service
class NoticeUseCase(
    private val notices: NoticeRepository,
    @Value("\${tovikeli.admin-username:admin}") private val adminUsername: String
) {
    private val levels=setOf("info","success","warning","event")
    private fun admin(username:String){if(username!=adminUsername)throw SecurityException("forbidden")}
    private fun url(s:String){if(s.isBlank()||s.startsWith("/"))return;val u=URI(s);require(u.scheme in setOf("http","https")&&!u.host.isNullOrBlank())}
    fun active()=notices.active()
    fun all(username:String):List<SiteNotice>{admin(username);return notices.all()}
    fun create(username:String,title:String,body:String,level:String,link:String,linkLabel:String,pinned:Boolean):SiteNotice{admin(username);require(title.trim().length in 1..120);require(body.length<=500);require(level in levels);require(linkLabel.length<=30);url(link);return notices.create(title.trim(),body,level,link.trim(),linkLabel.trim(),pinned)}
    fun update(username:String,id:Long,title:String?,body:String?,level:String?,link:String?,linkLabel:String?,active:Boolean?,pinned:Boolean?):SiteNotice?{admin(username);title?.let{require(it.trim().length in 1..120)};body?.let{require(it.length<=500)};level?.let{require(it in levels)};link?.let{url(it)};linkLabel?.let{require(it.length<=30)};return notices.update(id,title,body,level,link,linkLabel,active,pinned)}
    fun delete(username:String,id:Long):Boolean{admin(username);return notices.delete(id)}
}

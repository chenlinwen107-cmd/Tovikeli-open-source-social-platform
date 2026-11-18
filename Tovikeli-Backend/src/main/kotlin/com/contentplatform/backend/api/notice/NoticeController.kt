package com.contentplatform.backend.api.notice

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.notice.NoticeUseCase
import com.contentplatform.backend.repository.SiteNotice
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class NoticeRequest(val title:String,val body:String="",val level:String="info",val link:String="",val linkLabel:String="",val pinned:Boolean=false)
data class NoticePatch(val title:String?=null,val body:String?=null,val level:String?=null,val link:String?=null,val linkLabel:String?=null,val active:Boolean?=null,val pinned:Boolean?=null)

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["spring.datasource.url"])
@RequestMapping("/api/v1/notices")
class NoticeController(private val notices:NoticeUseCase){
    @GetMapping fun active():ResponseEntity<Any> = ResponseEntity.ok(mapOf("notices" to notices.active().map(::mapNotice)))
    @GetMapping("/all") fun all(req:HttpServletRequest):ResponseEntity<Any> = try{ResponseEntity.ok(mapOf("notices" to notices.all(CallerResolver.require(req).username).map(::mapNotice)))}catch(_:SecurityException){ResponseEntity.status(403).build()}
    @PostMapping fun create(@RequestBody b:NoticeRequest,req:HttpServletRequest):ResponseEntity<Any> = try{ResponseEntity.status(201).body(mapOf("notice" to mapNotice(notices.create(CallerResolver.require(req).username,b.title,b.body,b.level,b.link,b.linkLabel,b.pinned))))}catch(_:SecurityException){ResponseEntity.status(403).build()}catch(e:IllegalArgumentException){ResponseEntity.badRequest().body(mapOf("error" to (e.message?:"invalid request")))}
    @PutMapping("/{id}") fun update(@PathVariable id:Long,@RequestBody b:NoticePatch,req:HttpServletRequest):ResponseEntity<Any> = try{notices.update(CallerResolver.require(req).username,id,b.title,b.body,b.level,b.link,b.linkLabel,b.active,b.pinned)?.let{ResponseEntity.ok(mapOf("notice" to mapNotice(it)))}?:ResponseEntity.notFound().build()}catch(_:SecurityException){ResponseEntity.status(403).build()}catch(e:IllegalArgumentException){ResponseEntity.badRequest().body(mapOf("error" to (e.message?:"invalid request")))}
    @DeleteMapping("/{id}") fun delete(@PathVariable id:Long,req:HttpServletRequest):ResponseEntity<Void> = try{if(notices.delete(CallerResolver.require(req).username,id))ResponseEntity.noContent().build()else ResponseEntity.notFound().build()}catch(_:SecurityException){ResponseEntity.status(403).build()}
    private fun mapNotice(n:SiteNotice)=mapOf("id" to n.id,"title" to n.title,"body" to n.body,"level" to n.level,"link" to n.link,"linkLabel" to n.linkLabel,"active" to n.active,"pinned" to n.pinned,"createdAt" to n.createdAt,"updatedAt" to n.updatedAt)
}

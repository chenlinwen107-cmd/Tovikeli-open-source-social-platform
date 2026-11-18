package com.contentplatform.backend.api.forum

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.forum.ForumUseCase
import com.contentplatform.backend.application.history.BrowseHistoryUseCase
import com.contentplatform.backend.domain.common.PublicId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class CreateThreadRequest(val boardId:Int,val title:String,val content:String,val media:List<MediaRef> = emptyList())
data class MediaRef(val id:String)
data class EditThreadRequest(val title:String,val content:String)

@RestController
@RequestMapping("/api/v1/forum")
@ConditionalOnProperty(name=["spring.datasource.url"])
class ForumController(private val forum:ForumUseCase, private val history: BrowseHistoryUseCase){
 @GetMapping("/boards")
 fun boards(req:HttpServletRequest)=ResponseEntity.ok(mapOf("boards" to forum.boards(CallerResolver.resolve(req)?.userId)))
 @GetMapping("/my-boards")
 fun myBoards(req:HttpServletRequest):ResponseEntity<Any>{val u=CallerResolver.resolve(req)?.userId?:return ResponseEntity.ok(mapOf("boards" to emptyList<Any>()));return ResponseEntity.ok(mapOf("boards" to forum.boards(u).filter{it.isFollowing}))}
 @GetMapping("/boards/{slug}")
 fun board(@PathVariable slug:String,@RequestParam(defaultValue="latest") sort:String,req:HttpServletRequest):ResponseEntity<Any>{val r=forum.board(slug,CallerResolver.resolve(req)?.userId,sort)?:return ResponseEntity.notFound().build();return ResponseEntity.ok(mapOf("board" to r.first,"threads" to r.second))}
 @GetMapping("/threads")
 fun threads(@RequestParam(defaultValue="latest") sort:String,@RequestParam(defaultValue="0") offset:Int,@RequestParam(defaultValue="20") limit:Int,req:HttpServletRequest):ResponseEntity<Any>{if(offset<0||limit !in 1..100)return ResponseEntity.badRequest().build();val list=forum.threads(sort,CallerResolver.resolve(req)?.userId,limit,offset);return ResponseEntity.ok(mapOf("threads" to list,"hasMore" to(list.size==limit)))}
 @GetMapping("/threads/{id}")
 fun thread(@PathVariable id:String,req:HttpServletRequest):ResponseEntity<Any>{val t=forum.thread(PublicId(id),CallerResolver.resolve(req)?.userId)?:return ResponseEntity.notFound().build();CallerResolver.resolve(req)?.userId?.let { u -> history.record(u, "thread", id, t.title, "帖子", "/thread/$id", "message-square", t.author.nickname, "") };return ResponseEntity.ok(mapOf("thread" to t))}
 @PostMapping("/threads")
 fun create(@RequestBody r:CreateThreadRequest,req:HttpServletRequest):ResponseEntity<Any> = try{val t=forum.create(CallerResolver.require(req).userId,r.boardId.toString(),r.title,r.content,r.media.map{it.id});ResponseEntity.status(201).body(mapOf("thread" to t))}catch(e:IllegalArgumentException){ResponseEntity.badRequest().build()}
 @PutMapping("/threads/{id}")
 fun edit(@PathVariable id:String,@RequestBody r:EditThreadRequest,req:HttpServletRequest):ResponseEntity<Any> = try{val t=forum.edit(CallerResolver.require(req).userId,PublicId(id),r.title,r.content)?:return ResponseEntity.notFound().build();ResponseEntity.ok(mapOf("thread" to t))}catch(e:SecurityException){ResponseEntity.status(403).build()}catch(e:IllegalArgumentException){ResponseEntity.badRequest().build()}
 @PostMapping("/threads/{id}/like")
 fun like(@PathVariable id:String,req:HttpServletRequest):ResponseEntity<Any> = try{val t=forum.like(CallerResolver.require(req).userId,PublicId(id))?:return ResponseEntity.notFound().build();ResponseEntity.ok(mapOf("liked" to t.liked,"likeCount" to t.likeCount))}catch(e:RuntimeException){ResponseEntity.badRequest().build()}
 @PostMapping("/threads/{id}/subscribe")
 fun subscribe(@PathVariable id:String,req:HttpServletRequest)=ResponseEntity.ok(mapOf("subscribed" to forum.subscribe(CallerResolver.require(req).userId,PublicId(id))))
 @PostMapping("/boards/{id}/follow")
 fun followBoard(@PathVariable id:String,req:HttpServletRequest)=ResponseEntity.ok(mapOf("following" to forum.followBoard(CallerResolver.require(req).userId,id)))
 @PostMapping("/threads/{id}/moderate")
 fun moderate(@PathVariable id:String,@RequestBody body:Map<String,String>,req:HttpServletRequest):ResponseEntity<Any>{val u=CallerResolver.require(req).userId;val t=forum.thread(PublicId(id),u)?:return ResponseEntity.notFound().build();if(t.author.id!=u.value)return ResponseEntity.status(403).build();val action=body["action"]?:return ResponseEntity.badRequest().build();if(action=="delete"){forum.remove(u,PublicId(id));return ResponseEntity.ok(mapOf("deleted" to true))};return forum.moderate(PublicId(id),action)?.let{ResponseEntity.ok(mapOf("pinned" to it.pinned,"elite" to it.elite,"locked" to it.locked))}?:ResponseEntity.notFound().build()}
}
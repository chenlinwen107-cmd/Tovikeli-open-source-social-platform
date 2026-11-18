package com.contentplatform.backend.api.qa

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.qa.QaUseCase
import com.contentplatform.backend.application.history.BrowseHistoryUseCase
import com.contentplatform.backend.domain.common.PublicId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class CreateQaQuestionRequest(val title:String,val body:String,val category:String)
data class CreateQaAnswerRequest(val content:String)

@RestController
@RequestMapping("/api/v1/qa")
@ConditionalOnProperty(name=["spring.datasource.url"])
class QaController(private val qa:QaUseCase, private val history: BrowseHistoryUseCase){
 @GetMapping
 fun list(@RequestParam(required=false) category:String?,@RequestParam(required=false) status:String?,@RequestParam(defaultValue="latest") sort:String,@RequestParam(defaultValue="20") limit:Int,req:HttpServletRequest):ResponseEntity<Any>{
  if(limit !in 1..100)return ResponseEntity.badRequest().build()
  return ResponseEntity.ok(mapOf("questions" to qa.questions(category,status,sort,CallerResolver.resolve(req)?.userId,limit)))
 }
 @GetMapping("/{id}")
 fun detail(@PathVariable id:String,req:HttpServletRequest):ResponseEntity<Any>{
  val r=qa.detail(PublicId(id),CallerResolver.resolve(req)?.userId)?:return ResponseEntity.notFound().build()
  CallerResolver.resolve(req)?.userId?.let { u -> history.record(u, "question", id, r.first.title, "问答", "/qa/$id", "help-circle", r.first.author.nickname, "" ) }
  return ResponseEntity.ok(mapOf("question" to r.first,"answers" to r.second))
 }
 @PostMapping
 fun create(@RequestBody r:CreateQaQuestionRequest,req:HttpServletRequest):ResponseEntity<Any> = try{
  val q=qa.create(CallerResolver.require(req).userId,r.title,r.body,r.category)
  ResponseEntity.status(201).body(mapOf("question" to q))
 }catch(e:IllegalArgumentException){ResponseEntity.badRequest().build()}
 @PostMapping("/{id}/answers")
 fun answer(@PathVariable id:String,@RequestBody r:CreateQaAnswerRequest,req:HttpServletRequest):ResponseEntity<Any> = try{
  val a=qa.answer(CallerResolver.require(req).userId,PublicId(id),r.content)
  ResponseEntity.status(201).body(mapOf("answer" to a))
 }catch(e:IllegalArgumentException){ResponseEntity.badRequest().build()}
 @PostMapping("/answers/{id}/vote")
 fun vote(@PathVariable id:String,req:HttpServletRequest):ResponseEntity<Any> = try{
  val a=qa.vote(CallerResolver.require(req).userId,PublicId(id))?:return ResponseEntity.notFound().build()
  ResponseEntity.ok(mapOf("voted" to a.voted,"voteCount" to a.voteCount))
 }catch(e:RuntimeException){ResponseEntity.badRequest().build()}
 @PostMapping("/{id}/accept/{answerId}")
 fun accept(@PathVariable id:String,@PathVariable answerId:String,req:HttpServletRequest):ResponseEntity<Any> = try{
  val a=qa.accept(CallerResolver.require(req).userId,PublicId(id),PublicId(answerId))?:return ResponseEntity.notFound().build()
  ResponseEntity.ok(mapOf("accepted" to a.accepted,"answer" to a))
 }catch(e:SecurityException){ResponseEntity.status(403).build()}
}
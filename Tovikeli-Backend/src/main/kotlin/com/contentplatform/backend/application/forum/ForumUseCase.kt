package com.contentplatform.backend.application.forum

import com.contentplatform.backend.application.content.DeleteContentUseCase
import com.contentplatform.backend.application.content.EditContentUseCase
import com.contentplatform.backend.application.content.PublishContentUseCase
import com.contentplatform.backend.application.interaction.CreateInteractionUseCase
import com.contentplatform.backend.application.interaction.DeleteInteractionUseCase
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.interaction.InteractionType
import com.contentplatform.backend.repository.ForumRepository
import com.contentplatform.backend.repository.ForumThread
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name=["spring.datasource.url"])
class ForumUseCase(
 private val forums:ForumRepository,
 private val publish:PublishContentUseCase,
 private val editContent:EditContentUseCase,
 private val deleteContent:DeleteContentUseCase,
 private val createLike:CreateInteractionUseCase,
 private val deleteLike:DeleteInteractionUseCase
){
 fun boards(userId:PublicId?)=forums.listBoards(userId)
 fun board(slug:String,userId:PublicId?,sort:String)=forums.findBoardBySlug(slug,userId)?.let{it to forums.listThreads(sort,it.id,userId,100,0)}
 fun threads(sort:String,userId:PublicId?,limit:Int,offset:Int)=forums.listThreads(sort,null,userId,limit,offset)
 fun searchThreads(query:String,limit:Int)=forums.searchThreads(query,limit)
 fun thread(id:PublicId,userId:PublicId?)=forums.findThread(id,userId)
 fun create(userId:PublicId,boardId:String,title:String,content:String,media:List<String>):ForumThread{
  require(title.trim().isNotBlank()&&title.length<=60){"invalid title"}
  require(content.trim().isNotBlank()&&content.length<=20000){"invalid content"}
  require(media.distinct().size==media.size&&media.size<=9){"invalid media"}
  require(forums.findBoardById(boardId)!=null){"board not found"}
  val saved=publish.publish(userId,content,media.map(::PublicId))
  return forums.saveThread(saved.id,boardId,title,saved.createdAt)
 }
 fun edit(userId:PublicId,id:PublicId,title:String,content:String):ForumThread?{
  val old=forums.findThread(id,userId)?:return null
  if(old.author.id!=userId.value)throw SecurityException("forbidden")
  require(title.trim().isNotBlank()&&title.length<=60&&content.trim().isNotBlank()&&content.length<=20000)
  editContent.edit(id,userId,content)
  forums.updateTitle(id,title)
  return forums.findThread(id,userId)
 }
 fun remove(userId:PublicId,id:PublicId):Boolean{
  val old=forums.findThread(id,userId)?:return false
  if(old.author.id!=userId.value)throw SecurityException("forbidden")
  deleteContent.delete(id,userId)
  forums.deleteThread(id)
  return true
 }
 fun like(userId:PublicId,id:PublicId):ForumThread?{
  val t=forums.findThread(id,userId)?:return null
  if(t.liked)deleteLike.delete(userId,id,InteractionType.LIKE) else createLike.create(userId,id,InteractionType.LIKE)
  return forums.findThread(id,userId)
 }
 fun followBoard(userId:PublicId,id:String)=forums.toggleBoardFollow(id,userId)
 fun subscribe(userId:PublicId,id:PublicId)=forums.toggleThreadSubscribe(id,userId)
 fun moderate(id:PublicId,action:String)=forums.moderate(id,action)
}
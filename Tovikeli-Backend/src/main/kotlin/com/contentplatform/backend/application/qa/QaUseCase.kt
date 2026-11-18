package com.contentplatform.backend.application.qa

import com.contentplatform.backend.application.content.PublishContentUseCase
import com.contentplatform.backend.application.interaction.CreateInteractionUseCase
import com.contentplatform.backend.application.interaction.DeleteInteractionUseCase
import com.contentplatform.backend.application.notification.NotificationUseCase
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.interaction.InteractionType
import com.contentplatform.backend.repository.QaAnswer
import com.contentplatform.backend.repository.QaQuestion
import com.contentplatform.backend.repository.QaRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name=["spring.datasource.url"])
class QaUseCase(
 private val qa:QaRepository,
 private val publish:PublishContentUseCase,
 private val createLike:CreateInteractionUseCase,
 private val deleteLike:DeleteInteractionUseCase,
 private val notifications:NotificationUseCase
){
 fun questions(category:String?,status:String?,sort:String,userId:PublicId?,limit:Int)=qa.listQuestions(category,status,sort,userId,limit)
 fun detail(id:PublicId,userId:PublicId?):Pair<QaQuestion,List<QaAnswer>>?=qa.findQuestion(id,userId)?.let{it to qa.listAnswers(id,userId)}
 fun create(userId:PublicId,title:String,body:String,category:String):QaQuestion{
  require(title.trim().isNotBlank()&&title.trim().length<=60){"invalid title"}
  require(body.trim().isNotBlank()&&body.length<=2000){"invalid body"}
  require(category in setOf("综合","技术","生活","情感","职场","校园","数码")){"invalid category"}
  val content=publish.publish(userId,body)
  return qa.saveQuestion(content.id,title,category,System.currentTimeMillis())
 }
 fun answer(userId:PublicId,questionId:PublicId,body:String):QaAnswer{
  require(body.trim().isNotBlank()&&body.length<=20000){"invalid answer"}
  require(qa.findQuestion(questionId,userId)!=null){"question not found"}
  val content=publish.publish(userId,body)
  return qa.saveAnswer(content.id,questionId,System.currentTimeMillis())
 }
 fun vote(userId:PublicId,answerId:PublicId):QaAnswer?{
  val answer=qa.findAnswer(answerId,userId)?:return null
  if(answer.voted) deleteLike.delete(userId,answerId,InteractionType.LIKE) else {
   createLike.create(userId,answerId,InteractionType.LIKE)
   if(answer.author.id!=userId.value) notifications.create(PublicId(answer.author.id),userId,"like","question",answer.questionId,answer.content.take(120))
  }
  return qa.findAnswer(answerId,userId)
 }
 fun accept(userId:PublicId,questionId:PublicId,answerId:PublicId):QaAnswer?{
  val question=qa.findQuestion(questionId,userId)?:return null
  if(question.author.id!=userId.value)throw SecurityException("forbidden")
  val answer=qa.findAnswer(answerId,userId)?:return null
  if(answer.questionId!=questionId.value)return null
  qa.accept(questionId,answerId)
  if(answer.author.id!=userId.value) notifications.create(PublicId(answer.author.id),userId,"accept","question",questionId.value)
  return qa.findAnswer(answerId,userId)
 }
}
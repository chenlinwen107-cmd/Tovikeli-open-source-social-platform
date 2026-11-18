package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId

data class QaAuthor(val id:String,val username:String,val nickname:String,val avatar:String?=null)
data class QaQuestion(val id:String,val title:String,val body:String,val excerpt:String,val category:String,val status:String,val author:QaAuthor,val answerCount:Long,val viewCount:Long,val createdAt:Long,val bestAnswerId:String?=null,val isAsker:Boolean=false)
data class QaAnswer(val id:String,val questionId:String,val content:String,val author:QaAuthor,val voteCount:Long,val voted:Boolean,val accepted:Boolean,val createdAt:Long)

interface QaRepository {
 fun listQuestions(category:String?,status:String?,sort:String,userId:PublicId?,limit:Int):List<QaQuestion>
 fun findQuestion(id:PublicId,userId:PublicId?):QaQuestion?
 fun listAnswers(questionId:PublicId,userId:PublicId?):List<QaAnswer>
 fun saveQuestion(id:PublicId,title:String,category:String,createdAt:Long):QaQuestion
 fun saveAnswer(id:PublicId,questionId:PublicId,createdAt:Long):QaAnswer
 fun accept(questionId:PublicId,answerId:PublicId):Boolean
 fun findAnswer(id:PublicId,userId:PublicId?):QaAnswer?
}
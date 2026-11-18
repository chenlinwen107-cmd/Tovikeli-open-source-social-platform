package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId

data class ForumBoard(val id:String,val slug:String,val name:String,val description:String,val icon:String,val announcement:String?,val parentId:String?,val threadCount:Long,val followers:Long,val isFollowing:Boolean,val children:List<ForumBoard> = emptyList())
data class ForumAuthor(val id:String,val username:String,val nickname:String,val avatar:String?=null)
data class ForumMedia(val id:String,val url:String,val type:String)
data class ForumThread(val id:String,val board:ForumBoard,val author:ForumAuthor,val title:String,val content:String,val media:List<ForumMedia>,val pinned:Boolean,val elite:Boolean,val locked:Boolean,val views:Long,val replyCount:Long,val likeCount:Long,val createdAt:Long,val lastReplyAt:Long,val liked:Boolean=false,val isSubscribed:Boolean=false,val canModerate:Boolean=false)

interface ForumRepository {
 fun listBoards(userId:PublicId?):List<ForumBoard>
 fun findBoardBySlug(slug:String,userId:PublicId?):ForumBoard?
 fun findBoardById(id:String):ForumBoard?
 fun listThreads(sort:String,boardId:String?,userId:PublicId?,limit:Int,offset:Int):List<ForumThread>
 fun searchThreads(query:String,limit:Int):List<ForumThread>
 fun findThread(id:PublicId,userId:PublicId?):ForumThread?
 fun saveThread(id:PublicId,boardId:String,title:String,now:Long):ForumThread
 fun updateTitle(id:PublicId,title:String):Boolean
 fun deleteThread(id:PublicId):Boolean
 fun toggleBoardFollow(boardId:String,userId:PublicId):Boolean
 fun toggleThreadSubscribe(threadId:PublicId,userId:PublicId):Boolean
 fun moderate(id:PublicId,action:String):ForumThread?
}
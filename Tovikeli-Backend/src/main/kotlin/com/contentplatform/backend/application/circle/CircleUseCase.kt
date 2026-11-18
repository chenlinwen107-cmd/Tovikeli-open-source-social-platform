package com.contentplatform.backend.application.circle

import com.contentplatform.backend.domain.content.ContentLifecycleResult
import com.contentplatform.backend.application.content.DeleteContentUseCase
import com.contentplatform.backend.application.content.PublishContentUseCase
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.CircleRepository
import org.springframework.stereotype.Service

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["spring.datasource.url"])
class CircleUseCase(private val circles:CircleRepository,private val publish:PublishContentUseCase,private val deleteContent:DeleteContentUseCase){
 private val categories=setOf("兴趣","科技","生活","创作","同城")
 private val icons=setOf("compass","camera","code","rocket","heart","book","fire","edit","forum","coin")
 private val colors=setOf("#2b54f0","#7c3aed","#059f76","#ef6c12","#e11d6b","#0e8fb8","#f59e0b")
 fun list(category:String?,userId:PublicId?)=circles.list(category,userId)
 fun get(slug:String,userId:PublicId?)=circles.findBySlug(slug,userId)
 fun get(id:Long,userId:PublicId?)=circles.findById(id,userId)
 fun members(id:Long)=circles.members(id)
 fun postIds(id:Long)=circles.postIds(id)
 fun messages(id:Long)=circles.chat(id)
 fun create(userId:PublicId,name:String,description:String,category:String,icon:String,color:String)=run{
  val n=name.trim();require(n.isNotEmpty()&&n.length<=24);require(category in categories&&icon in icons&&color in colors)
  val base=n.lowercase().replace(Regex("[^a-z0-9]+"),"-").trim('-').take(40).ifBlank{"circle"}
  circles.create(n,base+"-"+System.currentTimeMillis(),description.trim().take(200),category,icon,color,userId,System.currentTimeMillis())
 }
 fun toggleMember(userId:PublicId,circleId:Long):Boolean{require(circles.findById(circleId,null)!=null);return circles.toggleMember(circleId,userId)}
 fun isMember(userId:PublicId,circleId:Long)=circles.isMember(circleId,userId)
 fun post(userId:PublicId,circleId:Long,text:String,media:List<String>):PublicId{require(circles.findById(circleId,null)!=null);require(circles.isMember(circleId,userId));require(text.trim().isNotBlank()&&text.length<=20000);require(media.distinct().size==media.size&&media.size<=9);val c=publish.publish(userId,text,media.map(::PublicId));circles.addPost(circleId,c.id,c.createdAt);return c.id}
 fun deletePost(userId:PublicId,circleId:Long,contentId:PublicId):Boolean{require(circles.isMember(circleId,userId));return deleteContent.delete(contentId,userId)==ContentLifecycleResult.SUCCESS}
 fun chat(userId:PublicId,circleId:Long,text:String)=run{require(circles.isMember(circleId,userId));val t=text.trim();require(t.isNotBlank()&&t.length<=1000);circles.addChat(circleId,userId,t,System.currentTimeMillis())}
}
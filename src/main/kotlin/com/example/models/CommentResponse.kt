package bazaar.news.com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class CommentResponse(
    val id: Int,
    val postId: Int,
    val authorId: Int,
    val content: String,
    val createdAt: String,
    val authorUsername: String
)
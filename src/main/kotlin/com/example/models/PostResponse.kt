package bazaar.news.com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class PostResponse(
    val id: Int,
    val title: String,
    val content: String,
    val authorId: Int,
    val createdAt: String,
    val x: Double,
    val y: Double,
    val traction: Int,
    val color: String,
    val radius: Double,
)

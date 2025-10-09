package bazaar.news.com.example.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object Comments : Table("comments") {
    val id = integer(name = "id").autoIncrement()
    val postId = integer(name = "postId").references(Posts.id)
    val authorId = integer(name = "authorId").references(Users.id)
    val content = text("content")
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}


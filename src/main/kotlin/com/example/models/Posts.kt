package bazaar.news.com.example.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object Posts : Table("posts") {
    val id = integer(name = "id").autoIncrement()
    val title = varchar("title", 255)
    val content = text("content")
    val createdAt = datetime("created_at")
    val authorId = integer(name = "author_id").references(Users.id)
    override val primaryKey = PrimaryKey(id)
}


package bazaar.news.com.example.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object Posts : Table("posts") {
    val id = integer(name = "id").autoIncrement()
    val title = varchar("title", 255)
    val content = text("content")
    val createdAt = datetime("created_at")
    val authorId = integer(name = "author_id").references(Users.id)

    val x = double("x").default(0.0)
    val y = double("y").default(0.0)

    val traction = integer("traction").default(0)

    val color = varchar("color", 50).default("rgb(128, 128, 128)")
    val radius = double("radius").default(75.0)

    override val primaryKey = PrimaryKey(id)
}


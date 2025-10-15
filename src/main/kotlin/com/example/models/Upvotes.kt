package bazaar.news.com.example.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * One row per (post_id, user_id). Composite PK enforces "only once".
 */
object Upvotes : Table("upvotes") {
    val postId = integer("post_id")
        .references(Posts.id, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    val userId = integer("user_id")
        .references(Users.id, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(postId, userId, name = "pk_upvotes")

    init {
        index(isUnique = false, columns = arrayOf(postId))
        index(isUnique = false, columns = arrayOf(userId))
    }
}
